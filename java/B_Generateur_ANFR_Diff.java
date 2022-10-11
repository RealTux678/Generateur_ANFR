import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**************************************************************************************
 * Created by Tristan on 09/04/21.
 * Updated by Tristan on 11/10/22 : Mode 1 point par opérateur (carte multi-layers)
 *
 * Recherche les différences entre 2 bases ANFR
 * Pré-requis: 2 bases SQL dans /SQL/historique (avec un numéro de version différent)
 * Génère : diff.js
 *************************************************************************************/

public class B_Generateur_ANFR_Diff {

    private String TAG = "ANFR Diff ";
    private SQLiteConnect_Aa dbAa;
    private SQLiteConnect_Histo dbHt_1;             //doit être le plus récent
    private SQLiteConnect_Histo dbHt_2;

    private String file1, file2;
    
    private final String jsFileName = "diff_1p_op.js";

    private double sqlLat, sqlLon;
    private String sqlData;
    private String sqlVersion;                      // Dataset de file1


    protected B_Generateur_ANFR_Diff() {
        Main.writeLog(TAG);
        generateur();
        System.out.println(TAG+"Terminé !");
    }


    private void generateur() {
        if (checkBases()) {
            dbAa = new SQLiteConnect_Aa();
            dbAa.connect();
            dbAa.sql_query("CREATE TABLE Nouvelles (ID INTEGER PRIMARY KEY AUTOINCREMENT, PLMN INTEGER, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, DateAct TEXT, Syst SMALLINT, Flag TEXT, OP TEXT)"); //OP utilisé poue différencier l'operateur d'Outre-mer
            dbAa.sql_query("CREATE TABLE OLD (ID INTEGER PRIMARY KEY AUTOINCREMENT, OP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst SMALLINT)");
            dbAa.sql_query("CREATE TABLE NEW (ID INTEGER PRIMARY KEY AUTOINCREMENT, OP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst SMALLINT)");

            dbHt_1 = new SQLiteConnect_Histo();
            dbHt_1.restore(file1);

            dbHt_2 = new SQLiteConnect_Histo();
            dbHt_2.restore(file2);

            System.out.println("\n1. "+file1);
            System.out.println("----------------");
            System.out.println("1. Orange " + dbHt_1.numberRows("20801"));
            System.out.println("1. SFR    " + dbHt_1.numberRows("20810"));
            System.out.println("1. Free   " + dbHt_1.numberRows("20815"));
            System.out.println("1. ByTel  " + dbHt_1.numberRows("20820"));
            System.out.println("1. OutreM " + dbHt_1.numberRows("99999") + "\n");

            System.out.println("2. "+file2);
            System.out.println("----------------");
            System.out.println("2. Orange " + dbHt_2.numberRows("20801"));
            System.out.println("2. SFR    " + dbHt_2.numberRows("20810"));
            System.out.println("2. Free   " + dbHt_2.numberRows("20815"));
            System.out.println("2. ByTel  " + dbHt_2.numberRows("20820"));
            System.out.println("2. OutreM " + dbHt_2.numberRows("99999") + "\n");
            
            //bloquer si la base plus récente contient le même nombre d'enregistrements ou moins
            int totF1 = dbHt_1.numberRows("20801")+dbHt_1.numberRows("20810")+dbHt_1.numberRows("20815")+dbHt_1.numberRows("20820")+dbHt_1.numberRows("99999");
            int totF2 = dbHt_2.numberRows("20801")+dbHt_2.numberRows("20810")+dbHt_2.numberRows("20815")+dbHt_2.numberRows("20820")+dbHt_2.numberRows("99999");
            if (totF1 <= totF2) {
                System.out.println(Main.ANSI_RED + "Erreur: "+file1+" ("+totF1+") <= "+file2+" ("+totF2+")" + Main.ANSI_RESET);
                return;
            }
            
            File file = new File(Main.ABS_PATH +"/Generated");
            if (!file.exists())
                file.mkdirs();

            File f = new File(Main.ABS_PATH+"/Generated/"+jsFileName);
            f.delete();
            // écrire l'entête du fichier JS
            writeFile(jsFileName,"//Generated "+Main.dateLog()+" "+Main.timeLog());
            writeFile(jsFileName,"var addressPoints = [");

            setDataset();


            /* ------ Déclarations / Activations ------- */
            parseMethod2();
            writeData(1);    //écrire les données vers diff.js
            stats();        //statistiques
            
            int news = dbAa.numberRowsQ("SELECT COUNT(*) AS rowcount FROM Nouvelles WHERE xG = 4 AND Flag = 'N'");
            int acti = dbAa.numberRowsQ("SELECT COUNT(*) AS rowcount FROM Nouvelles WHERE xG = 4 AND Flag = 'A'");
            int news5 = dbAa.numberRowsQ("SELECT COUNT(*) AS rowcount FROM Nouvelles WHERE xG = 5 AND Flag = 'N'");
            int acti5 = dbAa.numberRowsQ("SELECT COUNT(*) AS rowcount FROM Nouvelles WHERE xG = 5 AND Flag = 'A'");

            dbHt_1.close(); //fermer la connexion
            dbHt_2.close(); //fermer la connexion


            /* ------ Suppressions / Extinctions ------- */
            dbHt_1 = new SQLiteConnect_Histo();
            dbHt_1.restore(file2);                  // recharger les bases avec les fichiers inversés
            dbHt_2 = new SQLiteConnect_Histo();
            dbHt_2.restore(file1);                  // recharger les bases avec les fichiers inversés
            dbAa.delete("Nouvelles");
            parseMethod2();
            writeData(0);    //écrire les données vers diff.js
            dbHt_1.close(); //fermer la connexion
            dbHt_2.close(); //fermer la connexion


            /* ------ Statistiques ------- */
            writeFile(jsFileName,"];\n"); //dernière ligne !
            writeFile(jsFileName, "var update = ['"+ Main.epoch2Date(Main.timestampMilis()/1000)+"'];");
            writeFile(jsFileName, "var dataset = ['"+ sqlVersion+"'];");
            writeFile(jsFileName,"var news = ['"+news+"'];");
            writeFile(jsFileName,"var acti = ['"+acti+"'];");
            writeFile(jsFileName,"var news5 = ['"+news5+"'];");
            writeFile(jsFileName,"var acti5 = ['"+acti5+"'];");
            writeFile(jsFileName,"var supp = ['"+dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'N'")+"'];");
            writeFile(jsFileName,"var off = ['"+dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A'")+"'];");
            writeFile(jsFileName,"var supp5 = ['"+dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'N'")+"'];");
            writeFile(jsFileName,"var off5 = ['"+dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'A'")+"'];");


            dbAa.close();   //fermer la connexion
        }
    }



    private void setDataset() {
        ResultSet rs = dbHt_1.queryRs("SELECT * FROM Version");
        try {
            while (rs.next()) {
                sqlVersion = rs.getString(3)+" ["+rs.getString(2)+"]";
                //System.out.println("sqlVersion="+sqlVersion);
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    //récupérer les 2 bases à analyser
    private boolean checkBases() {
        try (Stream<Path> walk = Files.walk(Paths.get(Main.ABS_PATH + "/SQL/ANFR/"), 1)) { //mettre depth à 1 pour ne pas lire le contenu des sous-dosiers
            List<String> result = walk.map(Path::toString).filter(f -> f.endsWith(".db")).collect(Collectors.toList());
            Collections.sort(result);   //trier dansd l'ordre

            if (result.size() >= 2) {
                for (String n : result) {
                    String[] tokens = n.split(Pattern.quote(File.separator));  // Split by '/'
                    file2 = file1;
                    file1 = tokens[tokens.length - 1];
                }
                //System.out.println("file1=" + file1);
                //System.out.println("file2=" + file2);
                return true;
            } else {
                System.out.println(Main.ANSI_RED+TAG + "Erreur : il faut 2 bases"+Main.ANSI_RESET);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }



    // Nouvelle méthode par requêtes SQL du 04/06/21
    protected void parseMethod2() {
        dbAa.delete("OLD"); //utile car on revient ici une 2e fois pour les suppresions/extinctions
        dbAa.delete("NEW"); //utile car on revient ici une 2e fois pour les suppresions/extinctions
        copyData("20801", "", "");
        copyData("99999", " WHERE OP = 'ORA' ", "20801");
        parseData("20801");
        dbAa.delete("OLD");
        dbAa.delete("NEW");
        copyData("20810", "", "");
        copyData("99999", " WHERE OP = 'SRR' ", "20810");
        parseData("20810");
        dbAa.delete("OLD");
        dbAa.delete("NEW");
        copyData("20815", "", "");
        copyData("99999", " WHERE OP = 'FREE' ", "20815");
        parseData("20815");
        dbAa.delete("OLD");
        dbAa.delete("NEW");
        copyData("20820", "", "");
        parseData("20820");
        dbAa.delete("OLD");
        dbAa.delete("NEW");
        copyData("99999", " WHERE OP!='ORA' AND OP!='SRR' AND OP!='FREE' ", "");
        parseData("99999");
    }



    // envoyer les données dans une base unique
    private void copyData(String oper, String where, String overridePlmn) {
        int read=0;
        int lignes= dbHt_1.numberRows(oper) + dbHt_2.numberRows(oper);
        ResultSet rs = dbHt_1.queryRs("SELECT * FROM '"+oper+"'"+where+" ORDER BY AnfrID ASC");
        try {
            while (rs.next()) {
                updateProgressBar(lignes, read);
                read++;
                String op = oper;
                if (oper.equals("99999")) {
                    if (overridePlmn.length() > 0)
                        op = overridePlmn;
                    else
                        op = rs.getString(2);
                }
                String anfrData = rs.getString(6);
                anfrData = anfrData.replace("'","''");  //escape single quote before SQL insert !
                dbAa.sql_query("INSERT INTO NEW VALUES (NULL, '"+op+"', '"+rs.getString(3)+"', '"+rs.getString(4)+"', "+rs.getInt(5)+", '"+ anfrData +"', '"+rs.getString(7)+"', '"+rs.getString(8)+"', '"+rs.getString(9)+"', '"+rs.getString(10)+"', '"+rs.getString(11)+"', "+rs.getInt(12)+")");
            }
            rs.close();
            System.out.println(oper+": "+read+" lignes écrites                             ");

            ResultSet rs2 = dbHt_2.queryRs("SELECT * FROM '"+oper+"'"+where+" ORDER BY AnfrID ASC");
            int read2=0;
            while (rs2.next()) {
                updateProgressBar(lignes, (read+read2));
                read2++;
                String op = oper;
                if (oper.equals("99999")) {
                    if (overridePlmn.length() > 0)
                        op = overridePlmn;
                    else
                        op = rs2.getString(2);
                }
                String anfrData = rs2.getString(6);
                anfrData = anfrData.replace("'","''");  //escape single quote before SQL insert !
                dbAa.sql_query("INSERT INTO OLD VALUES (NULL, '"+op+"', '"+rs2.getString(3)+"', '"+rs2.getString(4)+"', "+rs2.getInt(5)+", '"+ anfrData +"', '"+rs2.getString(7)+"', '"+rs2.getString(8)+"', '"+rs2.getString(9)+"', '"+rs2.getString(10)+"', '"+rs2.getString(11)+"', "+rs2.getInt(12)+")");
            }
            rs2.close();
            System.out.println(oper+": "+read2+" lignes écrites                            ");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private void parseData(String oper) {
        //déclarations
        int read=0;
        //ne pas faire intervenir AnfrData au cas ou il y aurait eu une modification de typographie, idem pour lat et lon
        ResultSet rs = dbAa.queryRs("SELECT OP, AnfrID, xG, Syst FROM NEW EXCEPT SELECT OP, AnfrID, xG, Syst FROM OLD");
        try {
            while (rs.next()) {
                read++;
                int anfrId = rs.getInt(2);
                int xg = rs.getInt(3);
                int syst = rs.getInt(4);
                rechSql(oper, anfrId);  //récupérer anfrData, LAT, LON
                String anfrData = sqlData;
                anfrData = anfrData.replace("'","''");  //escape single quote before SQL insert !
                System.out.println(oper+" Nouvelle fréquence : anfrId="+anfrId+" syst="+syst+" "+xg+"G "+anfrData);
                dbAa.sql_query("INSERT INTO Nouvelles VALUES (NULL, '"+oper+"', '"+anfrId+"', '"+anfrData+"', '"+sqlLat+"', '"+sqlLon+"', '"+ xg +"', '', "+syst+", 'N', '"+rs.getString(1)+"')");
            }
            rs.close();
            System.out.println(oper+": "+read+" lignes lues");

            //activations
            read=0;
            //ne pas faire intervenir AnfrData au cas ou il y aurait eu une modification de typographie, idem pour lat et lon
            ResultSet rs1 = dbAa.queryRs("SELECT OP, AnfrID, xG, Syst FROM NEW WHERE Act = 1 INTERSECT SELECT OP, AnfrID, xG, Syst FROM OLD WHERE Act = 0");
            while (rs1.next()) {
                read++;
                int anfrId = rs1.getInt(2);
                int xg = rs1.getInt(3);
                int syst = rs1.getInt(4);
                rechSql(oper, anfrId);  //récupérer anfrData, LAT, LON
                String anfrData = sqlData;
                anfrData = anfrData.replace("'","''");  //escape single quote before SQL insert !
                System.out.println(oper+" Nouvelle activation: anfrId="+anfrId+" syst="+syst+" "+xg+"G "+anfrData);
                dbAa.sql_query("INSERT INTO Nouvelles VALUES (NULL, '"+oper+"', '"+anfrId+"', '"+anfrData+"', "+sqlLat+", "+sqlLon+", '"+ xg +"', '', "+syst+", 'A', '"+rs1.getString(1)+"')");
            }
            rs1.close();
            System.out.println(oper+": "+read+" lignes lues");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    //récupérer les données complémentaires
    private void rechSql(String oper, int anfrId) {
        sqlData = "";
        sqlLat = 0.0;
        sqlLon = 0.0;
        //ResultSet rs = dbHt_1.queryRs("SELECT AnfrData, LAT, LON FROM '"+oper+"' WHERE AnfrID = "+anfrId);
        ResultSet rs = dbAa.queryRs("SELECT AnfrData, LAT, LON FROM NEW WHERE AnfrID = "+anfrId);
        try {
            if (rs.next()) {
                sqlData= rs.getString(1);
                sqlLat = rs.getDouble(2);
                sqlLon = rs.getDouble(3);
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // écrire (avec gestion mono-opérateur par site)
    private void writeData(int ajout) {
        writeData(20801, ajout, 0);
        writeData(20810, ajout, 0);
        writeData(20815, ajout, 0);
        writeData(20820, ajout, 0);
        writeData(99999, ajout, 1);
        writeData(99999, ajout, 2);
        writeData(99999, ajout, 3);
        writeData(99999, ajout, 4);
    }

    private void writeData(int plmn, int ajout, int groupe) {
        int anfrIdCount=0;
        ResultSet rs0 = dbAa.queryRs("SELECT DISTINCT AnfrID FROM Nouvelles WHERE PLMN = "+plmn + requestSuffix(groupe));  //récupérer les ANFR ID
        try {
            while (rs0.next()) {
                anfrIdCount++;
                int anfrId = rs0.getInt(1);

                if (plmn==99999)
                    writeOm(ajout, anfrId, groupe);
                else
                    writeMonoPlmn(ajout, anfrId, plmn);
            }
            rs0.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println(anfrIdCount+" AnfrId détectés");
    }

    private String requestSuffix(int groupe) {
        if (groupe==1)
            return " AND (OP = 'DIGI' OR OP = 'MAOR' OR OP = 'VITI' OR OP = 'GLOB')";
        else if (groupe==2)
            return " AND (OP = 'OUTR' OR OP = 'TLOI' OR OP = 'ONAT' OR OP = 'SPM')";
        else if (groupe==3)
            return " AND (OP = 'DAU' OR OP = 'ZEOP')";
        else if (groupe==4)
            return " AND (OP = 'PMT' OR OP = 'BPT' OR OP = 'GOPT')";
        else
            return "";
    }


    protected void writeMonoPlmn(int ajout, int anfrId, int plmn) {
        String anfrData = "";
        String systDecl = "";
        String systActi = "";
        String lat="0.0", lon="0.0";
        String flag;

        ResultSet rs = dbAa.queryRs("SELECT * FROM Nouvelles WHERE AnfrID = "+anfrId+" AND PLMN = "+plmn);
        try {
            while (rs.next()) {
                String op=rs.getString(11);  // op dans la colonne CP pour l'outre-mer
                anfrId = rs.getInt(3);
                flag = rs.getString(10);
                lat = rs.getString(5);
                lon = rs.getString(6);
                anfrData = rs.getString(4);
                //System.out.println("#"+anfrId+" "+rs.getString(4)+" "+rs.getString(9));
                if (flag.equals("A")) {
                    if (systActi.length() > 0)      //faire un check préalable car il y a déjà eu des décl avant et donc on se retrouverait avec un "," en trop
                        systActi += ", ";           //append
                    systActi += rs.getString(9)+ " MHz ("+rs.getString(7)+"G)"; //append
                    if (plmn==99999)
                        systActi += " ["+op+"]";    //append
                } else {
                    if (systDecl.length() > 0)      //faire un check préalable car il y a déjà eu des décl avant et donc on se retrouverait avec un "," en trop
                        systDecl += ", ";           //append
                    systDecl += rs.getString(9)+ " MHz ("+rs.getString(7)+"G)"; //append
                    if (plmn==99999)
                        systDecl += " ["+op+"]";    //append
                }
            }

            if (systActi.length() == 0)
                systActi = "-";
            //else if (plmn!=99999)   //ne pas rajouter le MHz en outre mer car l'opérateur est intercalé entre
            //    systActi = systActi + " MHz";
            if (systDecl.length() == 0)
                systDecl = "-";
            //else if (plmn!=99999)
            //    systDecl = systDecl + " MHz";
            writeFile(jsFileName,"[" + lat + ", " + lon + ", " + plmn+", "+anfrId + ", \"" + anfrData + "\", \""+ systDecl  + "\", \""+ systActi + "\", "+ajout+"],");
            rs.close();
            //System.out.println(count+" lignes écrites");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    protected void writeOm(int ajout, int anfrId, int groupe) {
        int plmn;
        String op = "";
        String anfrData = "";
        String systDecl = "";
        String systActi = "";
        String systXg;
        String lat="0.0", lon="0.0";
        String flag;
        boolean act, decl;  //flage pour sacoir s'il y a eu activité pour un opérateur précis (et donc ajouter ou pas le suffice MHz)


        ResultSet rs0 = dbAa.queryRs("SELECT DISTINCT PLMN FROM Nouvelles WHERE PLMN = 99999 AND AnfrID = " + anfrId);
        try {
            while (rs0.next()) {
                plmn = rs0.getInt(1);
                //System.out.println(TAG + "anfrId="+anfrId+" "+rs0.getInt(1));
                decl = false;   //reset
                act = false;
                ResultSet rs = dbAa.queryRs("SELECT * FROM Nouvelles WHERE AnfrID = "+anfrId+" AND PLMN = "+plmn);
                while (rs.next()) {
                    lat = rs.getString(5);
                    lon = rs.getString(6);
                    anfrData = rs.getString(4);
                    flag = rs.getString(10);
                    plmn = rs.getInt(2);
                    systXg=rs.getString(7);
                    short freq = rs.getShort(9);
                    if (plmn==99999)
                        op = rs.getString(11);

                    if (flag.equals("A")) {
                        act = true;
                        if (systActi.length() > 0)
                            systActi += ", ";           //append
                        systActi += rs.getString(9)+ "&nbsp;MHz";  //append
                        if (freq==700|freq==2100)
                            systActi += " ("+systXg+"G)";  //pour éviter l'ambiguité entre 4G et 5G
                    } else {
                        decl = true;
                        if (systDecl.length() > 0)
                            systDecl += ", ";           //append
                        systDecl += rs.getString(9)+ "&nbsp;MHz";  //append
                        if (freq==700|freq==2100)
                            systDecl += " ("+systXg+"G)";  //pour éviter l'ambiguité entre 4G et 5G
                    }
                }
                rs.close();

                if (act)
                    systActi = systActi + " [" + op + "]";
                if (decl)
                    systDecl = systDecl + " [" + op + "]";
            }

            if (systActi.length()==0)
                systActi = "-";

            if (systDecl.length()==0)
                systDecl = "-";

            writeFile(jsFileName,"[" + lat + ", " + lon + ", " + 9999+""+groupe +", "+anfrId + ", \"" + anfrData + "\", \""+ systDecl  + "\", \""+ systActi + "\", "+ajout+"],");
            rs0.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }





    private String convertOper(int plmn) {
        if (plmn==20801)
            return "Orange";
        else if (plmn==20810)
            return "SFR";
        else if (plmn==20815)
            return "Free";
        else if (plmn==20820)
            return "Bouygues";
        else
            return ""+plmn;
    }


    //afficher des statistiques
    protected void stats() {
        System.out.println();
        System.out.println("Declarat 4G " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'N'"));
        System.out.println("Declarat 5G " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'N'"));
        System.out.println("Activat. 4G " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A'"));
        System.out.println("Activat. 5G " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'A'"));

        System.out.println("Dec 4G  700 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'N' AND Syst = 700"));
        System.out.println("Dec 4G  800 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'N' AND Syst = 800"));
        System.out.println("Dec 4G 1800 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'N' AND Syst = 1800"));
        System.out.println("Dec 4G 2100 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'N' AND Syst = 2100"));
        System.out.println("Dec 4G 2600 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'N' AND Syst = 2600"));
        System.out.println("Act 4G  700 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 700"));
        System.out.println("Act 4G  800 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 800"));
        System.out.println("Act 4G 1800 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 1800"));
        System.out.println("Act 4G 2100 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 2100"));
        System.out.println("Act 4G 2600 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 2600"));

        System.out.println("Dec 5G  700 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'N' AND Syst = 700"));
        System.out.println("Dec 5G 2100 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'N' AND Syst = 2100"));
        System.out.println("Dec 5G 3500 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'N' AND Syst = 3500"));
        System.out.println("Act 5G  700 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'A' AND Syst = 700"));
        System.out.println("Act 5G 2100 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'A' AND Syst = 2100"));
        System.out.println("Act 5G 3500 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'A' AND Syst = 3500")+"\n");
        
        // déclarations 2100 par opérateur
        System.out.println("Dec 4G 2100 ORA  " + dbAa.numberRowsQ("SELECT COUNT(*) AS rowcount FROM Nouvelles WHERE PLMN = 20801 AND xG = 4 AND Flag = 'N' AND Syst = 2100"));
        System.out.println("Dec 4G 2100 SFR  " + dbAa.numberRowsQ("SELECT COUNT(*) AS rowcount FROM Nouvelles WHERE PLMN = 20810 AND xG = 4 AND Flag = 'N' AND Syst = 2100"));
        System.out.println("Dec 4G 2100 Free " + dbAa.numberRowsQ("SELECT COUNT(*) AS rowcount FROM Nouvelles WHERE PLMN = 20815 AND xG = 4 AND Flag = 'N' AND Syst = 2100"));
        System.out.println("Dec 4G 2100 ByTl " + dbAa.numberRowsQ("SELECT COUNT(*) AS rowcount FROM Nouvelles WHERE PLMN = 20820 AND xG = 4 AND Flag = 'N' AND Syst = 2100") + "\n");

        Main.writeLog("Act 4G  700 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 700"));
        Main.writeLog("Act 4G  800 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 800"));
        Main.writeLog("Act 4G 1800 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 1800"));
        Main.writeLog("Act 4G 2100 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 2100"));
        Main.writeLog("Act 4G 2600 " + dbAa.numberRowsW("Nouvelles", "xG = '4' AND Flag = 'A' AND Syst = 2600"));

        Main.writeLog("Dec 5G  700 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'N' AND Syst = 700"));
        Main.writeLog("Dec 5G 2100 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'N' AND Syst = 2100"));
        Main.writeLog("Dec 5G 3500 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'N' AND Syst = 3500"));
        Main.writeLog("Act 5G  700 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'A' AND Syst = 700"));
        Main.writeLog("Act 5G 2100 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'A' AND Syst = 2100"));
        Main.writeLog("Act 5G 3500 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'A' AND Syst = 3500"));

        //System.out.println("Act 5G 3500 " + dbAa.numberRowsW("Nouvelles", "xG = '5' AND Flag = 'A' AND Syst = 3500 AND PLMN = 20815")+"\n");
    }





    /***********************************************************************************************
     *                                METHODES COMMUNES
     **********************************************************************************************/

    private void writeFile(String file, String data) {
        try {
            FileWriter fw = new FileWriter(Main.ABS_PATH+"/Generated/"+file,true); //the true will append the new data
            fw.write(data+"\n");    //appends the string to the file
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //barre de progression
    private static void updateProgressBar(int total, int progress) {
        int prog = (progress*100/total);
        String display = "";
        for(int i = 1; i <= prog; i=i+2) {  //avancer par pas de 2 (50 divisions en tout)
            display += "=";
        }
        display += " "+prog +"%";
        System.out.print(display+"\r");
    }


}
