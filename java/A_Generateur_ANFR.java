import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**************************************************************************************
 * Générateur de bases ANFR pour eNB Analytics et Analytics.a
 * Updated by Tristan on 12/04/21.
 * Updated by Tristan on 12/10/21.
 *
 * Pré-requis: ANFR.csv, laposte_hexasmal.csv et SUP_ANTENNE.txt dans le dossier input
 * Génère: ANFR_1L_208xx.csv : fichiers pour eNB Analytics < v4.7.0.  Ne plus utiliser
 * Génère: ANFR_SQLite.db : base SQL 1l/Sup pour l'appli Android et autres usages
 * Génère: ANFR/yyyy-Sww_Ver.db : base SQL 1l/Freq pour base et suivi de l'historique
 *************************************************************************************/

public class A_Generateur_ANFR {

    private String TAG = "ANFR Gener ";
    private SQLiteConnect_Aa dbAa;                  //base SQL de travail (volatile)
    private SQLiteCnt_Android dbDr;                 //base SQL Android générée
    private SQLiteConnect_Histo dbAf;               //base SQL ANFR générée

    private String ligne;                           //ligne à écrire
    private String azimuthsDg = "";                 //azimuts depuis l'openData DataGouv
    private int hauteurDg = -1;                     //hauteur depuis l'openData DataGouv

    private int year;                               //Année
    private String week = "-1";                     //n° de  semaine. Initialiser à -1 car 0 est conforme (fin de la semaine 53 début janvier). Utiliser type String pour pouvoir rajouter un 0 pour les semaines 1 à 9
    public String dbFile = "";                      //nom du fichier SQL "Ht" contenant la version. Sera utilié pour recharger la bonne base en POST

    private byte OPERAT  = -1;                      //index colonnes CSV
    private byte SUP_ID  = -1;                      //initialiser à -1 pour pouvoir détecter une erreur dans l'auto-attribution
    private byte SYSTEME = -1;
    private byte SERVICE = -1;
    private byte C_DEPT  = -1;
    private byte C_INSEE = -1;
    private byte DATE_MAJ= -1;
    private byte STA_ID  = -1;
    private byte HAUTEUR = -1;
    private byte ADR_L   = -1;
    private byte ADR_1   = -1;
    private byte ADR_2   = -1;
    private byte C_POST  = -1;
    private byte COORD   = -1;



    protected A_Generateur_ANFR(String dbFile) {
        dbAa = new SQLiteConnect_Aa();
        dbAa.connect();
        dbAa.sql_query("CREATE TABLE Analytica (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, STA_NM_ANFR TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT, Haut SMALLINT)");
        dbAa.sql_query("CREATE TABLE laposte (ID INTEGER PRIMARY KEY AUTOINCREMENT, INSEE TEXT, Commune TEXT)");
        dbAa.sql_query("CREATE TABLE SUP_ANTENNE (ID INTEGER PRIMARY KEY AUTOINCREMENT, STA_NM_ANFR TEXT, TAE_ID INTEGER, AER_NB_AZIMUT TEXT, AER_NB_ALT_BAS SMALLINT)"); //pour la hauteur réelle de la station


        if (dbFile.length()==0) {
            // PASSE 1

            dbDr = new SQLiteCnt_Android();
            dbDr.connect();
            dbDr.updateDatabaseVersion(1);      // version doit TOUJOURS être à 1

            dbAf = new SQLiteConnect_Histo();
            dbAf.connect();

            //gestion des fichiers et checks préalables
            //year et week sont positionnés dans gestFichier()
            if (gestFichier()) {
                int anfrVersion = readPrevious();   // lire la base ANFR précédente, récupérer le n° de version et incrémenter
                System.out.println("Générateur ANFR -> "+week+" "+year+" ["+anfrVersion+"]");
                Main.writeLog("Générateur ANFR -> "+week+" "+year+" ["+anfrVersion+"]");

                dbDr.sql_query("INSERT INTO Version VALUES (NULL, " + anfrVersion + ", 'S"+week+" "+year+"')");
                dbAf.sql_query("INSERT INTO Version VALUES (NULL, " + anfrVersion + ", 'S"+week+" "+year+"')");

                communes2db();          //envoyer les communes dans la base SQL


                readAnfr("20801", "ORANGE", false);
                readAnfr("20810", "SFR", false);
                readAnfr("20815", "FREE MOBILE", false);
                readAnfr("20820", "BOUYGUES TELECOM", false);
                readAnfr("BPT", "SPT", true);
                readAnfr("DAU", "DAUPHIN TELECOM", true);
                readAnfr("DIGI", "DIGICEL", true);
                readAnfr("FREE", "FREE CARAIBES", true);
                readAnfr("GLOB", "GLOBALTEL", true);
                readAnfr("GOPT", "Gouv Nelle Calédonie (OPT)", true);
                readAnfr("MAOR", "MAORE MOBILE", true);
                readAnfr("ONAT", "ONATI", true);
                readAnfr("ORA", "ORANGE", true);
                readAnfr("OUTR", "OUTREMER TELECOM", true);
                readAnfr("PMT", "PMT/VODAFONE", true);
                readAnfr("SPM", "SPM TELECOM", true);
                readAnfr("SRR", "SRR", true);
                readAnfr("TLOI", "TELCO OI", true);
                readAnfr("VITI", "VITI SAS", true);
                readAnfr("ZEOP", "ZEOP", true);


                // SQL ANFR "Histo"
                this.dbFile = year+"-S"+week+"_"+anfrVersion+".db";
                dbAf.saveFile(this.dbFile);     //écrire le fichier pour pouvoir lancer le process diff

                // SQL Android (version provisoire)
                dbDr.saveFileProv();    //écrire le nouveau dans un fichier provisoire
                dbAf.close();
                dbDr.close();
            }
        } else if (gestFichierPost()) {
            // PASSE 2

            //int anfrVersion = readPrevious()-1; //lire la base ANFR précédente, récupérer le n° de version et PAS incrémenter en mode POST
            int anfrVersion;
            String[] tokens = dbFile.split("_");  // Split by '_'
            if (tokens.length==2) {
                String[] tokens2 = tokens[1].split("\\.");    //enlever le .db
                try {
                    anfrVersion =  Integer.parseInt(tokens2[0]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    return;
                }
            } else {
                System.out.println("Erreur dans le nom du fichier: "+dbFile);
                return;
            }

            System.out.println("Générateur ANFR POST -> "+dbFile+" ["+anfrVersion+"]");
            Main.writeLog("Générateur ANFR POST -> "+dbFile+" ["+anfrVersion+"]");
            dbDr = new SQLiteCnt_Android();
            dbDr.restoreProv();
            dbAf = new SQLiteConnect_Histo();
            dbAf.restore(dbFile);

            sup_Antenne2db();       //envoyer TAE_ID, AER_NB_AZIMUT et AER_NB_ALT_BAS dans SUP_ANTENNE

            processHautAz("20801"); //process à postiori afin de faire les diff avant. Attention encore plus lent que la méthode classique
            processHautAz("20810");
            processHautAz("20815");
            processHautAz("20820");
            processHautAz("99999"); //seulement les azimuths en l'Outre-Mer


            // SQL Android
            File fdbp = new File(Main.ABS_PATH +"/SQL/ANFR_SQLite_prov.db");
            fdbp.delete();      //éffacer le fichier SQL précédent
            File fdb = new File(Main.ABS_PATH +"/SQL/ANFR_SQLite.db");
            fdb.delete();       //éffacer le fichier SQL précédent
            File fdbx = new File(Main.ABS_PATH +"/SQL/ANFR_SQLite.db.xz");
            fdbx.delete();      //éffacer le fichier SQL précédent
            dbDr.saveFile();    //écrire le nouveau

            dbAf.close();
            dbDr.close();
        }

        System.out.println(TAG+"Terminé !");
        dbAa.close();  //fermer la connexion
    }


    // gestion des fichiers (passe 1) et vérifications. revoie false en cas de problème
    private boolean gestFichier() {
        //vérifier/créer les répertoires
        File file0 = new File(Main.ABS_PATH +"/input");
        if (!file0.exists())
            file0.mkdirs();

        File file = new File(Main.ABS_PATH +"/Generated");
        if (!file.exists())
            file.mkdirs();

        File file1 = new File(Main.ABS_PATH +"/SQL/ANFR");
        if (!file1.exists())
            file1.mkdirs();

        File file2 = new File(Main.ABS_PATH +"/input/ANFR.csv");
        if (!file2.exists()) {
            System.out.println(Main.ANSI_RED+"Erreur : Fichier ANFR.csv manquant"+Main.ANSI_RESET);
            Main.writeLog(TAG+"Erreur : Fichier ANFR.csv manquant");
            return false;
        }

        int lignes = Main.checkFileLines(Main.ABS_PATH +"/input/ANFR.csv");
        if (lignes <1000) {
            System.out.println(Main.ANSI_RED+"Erreur : ANFR.csv nombre de lignes incorrect"+Main.ANSI_RESET);
            Main.writeLog(TAG+"Erreur : ANFR.csv nombre de lignes incorrect");
            return false;
        }

        setColAndGetDataset();  //récupérer la date de MAJ et définir dynamiquement les colonnes
        if (OPERAT == -1)
            return false;       //Erreur dans l'attribution des colonnes
        
        if (week.equals("-1")) {
            System.out.println(Main.ANSI_RED+"Erreur décodage de la date"+Main.ANSI_RESET);
            Main.writeLog(TAG+"Erreur décodage de la date de ANFR.csv");
            return false;
        }

        File file3 = new File(Main.ABS_PATH +"/input/laposte_hexasmal.csv");
        if (!file3.exists()) {
            System.out.println(Main.ANSI_RED+"Erreur : Fichier laposte_hexasmal.csv manquant"+Main.ANSI_RESET);
            Main.writeLog(TAG+"Erreur : Fichier laposte_hexasmal.csv manquant");
            return false;
        }

        //éffacer éventuels fichiers précédents
        File f1l = new File(Main.ABS_PATH +"/Generated/ANFR_1L_20801.csv");
        f1l.delete();
        File f2l = new File(Main.ABS_PATH +"/Generated/ANFR_1L_20810.csv");
        f2l.delete();
        File f3l = new File(Main.ABS_PATH +"/Generated/ANFR_1L_20815.csv");
        f3l.delete();
        File f4l = new File(Main.ABS_PATH +"/Generated/ANFR_1L_20820.csv");
        f4l.delete();
        File fOm = new File(Main.ABS_PATH +"/Generated/ANFR_1L_99999.csv");
        fOm.delete();

        File fdb = new File(Main.ABS_PATH +"/SQL/ANFR_SQLite_prov.db");
        fdb.delete();   //éffacer le fichier SQL provisoire précédent. sera fait à la fin de la 2e passe, mais le faire déjà ici au cas ou il y aurait eu une interruption lors du précédent usage

        ligne = "2;"+year+";S"+week+";1234;";    //tokens[0] doit être à 2
        writeFile("Generated/ANFR_1L_20801.csv", ligne);
        writeFile("Generated/ANFR_1L_20810.csv", ligne);
        writeFile("Generated/ANFR_1L_20815.csv", ligne);
        writeFile("Generated/ANFR_1L_20820.csv", ligne);
        writeFile("Generated/ANFR_1L_99999.csv", ligne);

        ligne = "xg;sup_id;sta_nm_anfr;adr_cp;adr_full;latitude;longitude;service;emr_lb_systemes;emr_dt_service;sta_nm_haut;sta_azimuth";
        writeFile("Generated/ANFR_1L_20801.csv", ligne);
        writeFile("Generated/ANFR_1L_20810.csv", ligne);
        writeFile("Generated/ANFR_1L_20815.csv", ligne);
        writeFile("Generated/ANFR_1L_20820.csv", ligne);
        ligne = "xg;sup_id;sta_nm_anfr;adr_cp;adr_full;latitude;longitude;service;emr_lb_systemes;emr_dt_service;sta_nm_op";
        writeFile("Generated/ANFR_1L_99999.csv", ligne);

        return true;
    }


    // gestion des fichiers (passe 2) et vérifications. revoie false en cas de problème
    private boolean gestFichierPost() {
        //TODO : vérifier la présence du fichier SQL correspondant à la bonne date

        File file3 = new File(Main.ABS_PATH +"/input/SUP_ANTENNE.txt");
        if (!file3.exists()) {
            System.out.println(Main.ANSI_RED+"Erreur : Fichier SUP_ANTENNE.txt manquant"+Main.ANSI_RESET);
            Main.writeLog(TAG+"Erreur : Fichier SUP_ANTENNE.txt");
            return false;
        }
        return true;
    }


    //envoyer les communes dans la table SQL "laposte"
    private void communes2db() {
        File file = new File(Main.ABS_PATH +"/input/laposte_hexasmal.csv");
        InputStream is;
        try {
            is = new FileInputStream(file.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.ISO_8859_1));
            String line;
            try {
                reader.readLine();  //step over header
                while ((line = reader.readLine()) != null) {
                    String[] tokens = line.split(";");  // Split by ';'
                    tokens[1] = tokens[1].replace("'", "''");  //escape single quote before SQL insert !
                    dbAa.sql_query("INSERT INTO laposte VALUES (NULL, '"+tokens[0]+"', '"+tokens[1]+"')");
                }
                reader.close();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }



    private void readAnfr(String oper, String opLong, boolean isOm) {
        dbAa.delete("Analytica");  //éffacement des données SQL

        // Entrée : fichier "ANFR.csv" dans le dossier TowerUtils/input
        // Sortie : Base SQL, table ANFR
        File file = new File(Main.ABS_PATH +"/input/ANFR.csv");
        InputStream is;
        try {
            is = new FileInputStream(file.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line = "";
            String anfrData;
            String anfrAdresse1, anfrAdresse2, anfrAdresseLieu, cPostal;
            int step1Read=1,step2wrtite = 0;
            double anfrLat, anfrLon;
            int act;
            int xg;

            try {
                reader.readLine();  //step over header

                while ((line = reader.readLine()) != null) {
                    step1Read++;
                    //System.out.println(TAG+ "line="+line);
                    String[] tokens = line.split(";");  //split by ';'
                    String systeme = tokens[SYSTEME];

                    if(tokens[OPERAT].equals(opLong) && (systeme.contains("LTE") || systeme.contains("NR"))) {
                    //if(tokens[OPERAT].equals(opLong) && (systeme.contains("LTE") || systeme.contains("NR")|| systeme.contains("GSM"))) {

                        if (systeme.contains("NR"))
                            xg = 5;
                        else if (systeme.contains("GSM"))
                            xg = 2;
                        else
                            xg = 4;

                        // *** détection antennes inactives ***
                        if (!tokens[SERVICE].equals(""))
                            act = 1;
                        else
                            act = 0;

                        String anfrLatLon = tokens[COORD];
                        anfrLatLon = anfrLatLon.replace("\"", "");  //supprimer d'éventuels "  IMPORTANT (sinon le fichier JS sera corrompu)
                        //Découpage du champ coordonnées
                        String[] parts = anfrLatLon.split(", ");
                        try {
                            anfrLat = Double.parseDouble(parts[0]);    //converstion String en double
                            anfrLon = Double.parseDouble(parts[1]);
                        } catch (Exception e) {
                            // ArrayIndexOutOfBoundsException ou NumberFormatException
                            System.out.println(Main.ANSI_RED+"Erreur coordonnées GPS: \""+anfrLatLon+ "\" ligne "+step1Read+Main.ANSI_RESET);
                            continue;   //ne pas aller plus loin si coordonnées inexploitables. rajout 02/08/21
                        }

                        // arrondi (réel) à 4 décimales.
                        // important pour limiter le poids du fichier de représentation géographique
                        anfrLat = Math.round(anfrLat * 10000) / 10000.0;
                        anfrLon = Math.round(anfrLon * 10000) / 10000.0;

                        cPostal = tokens[C_POST];
                        anfrAdresse1 = tokens[ADR_1];
                        anfrAdresse2 = tokens[ADR_2];
                        anfrAdresseLieu = tokens[ADR_L];

                        //supprimer d'éventuels "
                        anfrAdresse1 = anfrAdresse1.replace("\"", "");
                        anfrAdresse2 = anfrAdresse2.replace("\"", "");
                        anfrAdresseLieu = anfrAdresseLieu.replace("\"", "");

                        if (anfrAdresse2.length() > 1)
                            anfrAdresse2 = anfrAdresse2 + " ";

                        if (cPostal.length()==4)
                            cPostal = "0"+cPostal;        // sur les dépts. 1 à 9 le 0x manque. OK pour le code INSEE par-contre

                        //System.out.println("tokens[16].length()="+anfrAdresse1.length()+" "+anfrAdresse1);
                        //System.out.println("tokens[17].length()="+anfrAdresse2.length()+" "+anfrAdresse2);

                        //mise en forme du nom du site
                        if (anfrAdresseLieu.equals("")) {
                            anfrData = anfrAdresse1 + " " + anfrAdresse2 + cPostal + " ";  //l'espace de fin est déjà dans le champ adresse2, si pas vide
                        } else if (anfrAdresse1.equals("")) {
                            anfrData = "(" + anfrAdresseLieu + ") " + cPostal + " ";
                        } else {
                            anfrData = anfrAdresse1 + " " + anfrAdresse2 + "(" + anfrAdresseLieu + ") " + cPostal + " ";  //l'espace de fin est déjà dans le champ adresse2, si pas vide
                        }

                        systeme = systeme.replace("5G NR ", "");
                        systeme = systeme.replaceAll("[^\\d.]", "");  //garder uniquement les caractères numériques

                        String[] tok = tokens[SERVICE].split("T");    //supprimer le 'T00:00:00'
                        String service = tok[0];

                        anfrData = anfrData.replace("'", "''");  //escape single quote before SQL insert !

                        // insert SQL
                        int departement;
                        try {
                            departement = Integer.parseInt(tokens[C_DEPT]);
                        } catch (NumberFormatException e) {
                            departement = 20;    //Corse
                        }

                        //hauteur. Attention ici c'est la hauteur du pylone et non celle de l'antenne
                        double haut = Double.parseDouble(tokens[HAUTEUR]);
                        if (haut > 70.0)
                            haut = 70.0;
                        int hauteur = (int) (haut+0.5d);

                        if (!isOm && departement<96 || isOm && departement>95) {    //utile car Orange est présent dans les 2 cas
                            dbAa.insertData_EA(cPostal, tokens[C_INSEE], tokens[STA_ID], Integer.parseInt(tokens[SUP_ID]), anfrData, anfrLat, anfrLon, xg, act, service, systeme, hauteur);
                            step2wrtite++;
                        }
                    }
                }
                System.out.println (oper+" Step 1 : "+step2wrtite+" Freq");
                reader.close(); //fermer le flux
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }



        // 2b : PARSE ANFR

        int anfrId, anfrIdPrec = 0;
        int read = 0;
        int step2write = 0;
        String anfrCpPrec = "";
        String anfrDataPrec = "";
        String stationId = "";
        String systemes = "";
        String systemes5= "";
        String dateActi = "";
        String dateAct5 = "";
        String hauteur = "";
        String azimuths="";
        double latPrec=0.0, lonPrec=0.0;
        int xg=4, actPrec=0;

        int lignes = dbAa.numberRows("Analytica");
        ResultSet rs = dbAa.queryRs("SELECT * FROM Analytica ORDER BY AnfrID ASC, xG ASC, Act DESC");
        try {
            while (rs.next()) {
                updateProgressBar(lignes, read);
                read++;
                anfrId = rs.getInt(5);

                if (anfrId != anfrIdPrec) {
                    // nouvelle ligne différente

                    if (anfrIdPrec > 0) {
                        step2write++;
                        if (isOm) {
                            ligne = "4;"+anfrIdPrec+";"+stationId+";"+anfrCpPrec+";"+anfrDataPrec+";"+latPrec+";"+lonPrec+";"+actPrec+";"+systemes+";"+dateActi+";"+oper;
                            writeFile("Generated/ANFR_1L_99999.csv", ligne);
                            anfrDataPrec = anfrDataPrec.replace("'","''");  //escape single quote before SQL insert !
                            dbDr.sql_query("INSERT INTO '99999' VALUES (NULL, "+ anfrIdPrec+", '"+anfrDataPrec+"', "+latPrec+", "+lonPrec+", "+actPrec+", '"+systemes+"', '"+dateActi+"', '"+systemes5+"', '"+dateAct5+"', '"+oper+"', '"+azimuths+"')");
                        } else {
                            ligne = "4;"+anfrIdPrec+";"+stationId+";"+anfrCpPrec+";"+anfrDataPrec+";"+latPrec+";"+lonPrec+";"+actPrec+";"+systemes+";"+dateActi+";"+hauteur+";"+azimuths;
                            writeFile("Generated/ANFR_1L_"+oper+".csv", ligne);
                            anfrDataPrec = anfrDataPrec.replace("'","''");  //escape single quote before SQL insert !
                            dbDr.sql_query("INSERT INTO '"+oper+"' VALUES (NULL, "+ anfrIdPrec+", '"+anfrDataPrec+"', "+latPrec+", "+lonPrec+", "+actPrec+", '"+systemes+"', '"+dateActi+"', '"+systemes5+"', '"+dateAct5+"', "+hauteur+", '"+azimuths+"')");
                        }
                    }

                    anfrIdPrec = anfrId;  // nouvelle réf

                    xg = rs.getInt(9);
                    if (xg==5) {
                        systemes = "";
                        dateActi = "";
                        systemes5= rs.getString(12);
                        dateAct5 = rs.getString(11);
                    } else if (xg==4) {
                        systemes = rs.getString(12);
                        dateActi = rs.getString(11);
                        systemes5= "";
                        dateAct5 = "";
                    }
                    hauteur = rs.getString(13);
                    anfrCpPrec = rs.getString(2);
                    anfrDataPrec = rs.getString(6);
                    String insee = rs.getString(3);
                    if (insee.length()==4)
                        insee = "0"+insee;        // sur les dépts. 1 à 9 le 0x manque. Anciens open data uniquemenrt
                    String commune = dbAa.getCommune(insee);  //faire la recherche de commune dans cette étape sinon on risque de recherche plusieurs fois la même commune
                    if (commune.equals("Commune")) {
                        System.out.println(Main.ANSI_RED+"Commune manquante INSEE="+rs.getString(3)+" ID="+anfrId+Main.ANSI_RESET);
                        Main.writeLog(TAG+"Commune manquante: INSEE="+rs.getString(3)+" ID="+anfrId);
                    }
                    anfrDataPrec = anfrDataPrec + commune;
                    anfrDataPrec = anfrDataPrec.replace("\"", "");  //sécurité supplémentaire. rajout 02/08/21
                    latPrec = rs.getDouble(7);
                    lonPrec = rs.getDouble(8);
                    actPrec = rs.getInt(10);
                    stationId = rs.getString(4);


                    String anfrDataSql = anfrDataPrec.replace("'","''");  //escape single quote before SQL insert !
                    if (isOm)
                        dbAf.insertData("99999", oper, rs.getString(3), stationId, anfrId, anfrDataSql, latPrec, lonPrec, xg, actPrec, rs.getString(11), rs.getString(12));   //Operateur à la place du code postal en OM
                    else
                        dbAf.insertData(oper, rs.getString(2), rs.getString(3), stationId, anfrId, anfrDataSql, latPrec, lonPrec, xg, actPrec, rs.getString(11), rs.getString(12));
                } else {
                    xg = rs.getInt(9);
                    if (xg==5) {
                        if (systemes5.length()>0) {         //faire un check préalable car il y a déjà eu la 4G avant et donc on se retrouverait avec un "," en trop
                            systemes5 += ",";               //append
                            dateAct5  += ",";               //append
                        }
                        systemes5 += rs.getString(12);      //append
                        dateAct5  += rs.getString(11);      //append
                    } else if (xg==4) {
                        // 4G
                        systemes += ","+rs.getString(12);   //append
                        dateActi += ","+rs.getString(11);   //append
                    }

                    String anfrDataSql = anfrDataPrec.replace("'","''");  //escape single quote before SQL insert !
                    if (isOm)
                        dbAf.insertData("99999", oper, rs.getString(3), rs.getString(4), anfrId, anfrDataSql, rs.getDouble(7), rs.getDouble(8), rs.getInt(9), rs.getInt(10), rs.getString(11), rs.getString(12));   //Operateur à la place du code postal en OM
                    else
                        dbAf.insertData(oper, rs.getString(2), rs.getString(3), rs.getString(4), anfrId, anfrDataSql, rs.getDouble(7), rs.getDouble(8), rs.getInt(9), rs.getInt(10), rs.getString(11), rs.getString(12));
                }
            }

            //écrire la dernière ligne
            step2write++;
            if (isOm) {
                ligne = "4;" + anfrIdPrec + ";" + stationId + ";" + anfrCpPrec + ";" + anfrDataPrec + ";" + latPrec + ";" + lonPrec + ";" + actPrec + ";" + systemes + ";" + dateActi + ";" + oper;
                writeFile("Generated/ANFR_1L_99999.csv", ligne);
                anfrDataPrec = anfrDataPrec.replace("'","''");  //escape single quote before SQL insert !
                dbDr.sql_query("INSERT INTO '99999' VALUES (NULL, "+ anfrIdPrec+", '"+anfrDataPrec+"', "+latPrec+", "+lonPrec+", "+actPrec+", '"+systemes+"', '"+dateActi+"', '"+systemes5+"', '"+dateAct5+"', '"+oper+"', '"+azimuths+"')");
            } else {
                ligne = "4;" + anfrIdPrec + ";" + stationId + ";" + anfrCpPrec + ";" + anfrDataPrec + ";" + latPrec + ";" + lonPrec + ";" + actPrec + ";" + systemes + ";" + dateActi + ";" + hauteur + ";" + azimuths;
                writeFile("Generated/ANFR_1L_" + oper + ".csv", ligne);
                anfrDataPrec = anfrDataPrec.replace("'", "''");  //escape single quote before SQL insert !
                dbDr.sql_query("INSERT INTO '" + oper + "' VALUES (NULL, " + anfrIdPrec + ", '" + anfrDataPrec + "', " + latPrec + ", " + lonPrec + ", " + actPrec + ", '" + systemes + "', '" + dateActi + "', '" + systemes5 + "', '" + dateAct5 + "', " + hauteur + ", '" + azimuths + "')");
            }

            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println (oper+" Step 2 : "+step2write+" Sup.                                  ");
        Main.writeLog(TAG+oper+" -> "+step2write+" supports");
    }



    private void sup_Antenne2db() {
        dbAa.delete("Analytica");

        //envoyer d'abord dans la table Analytica pour pouvoir faire un tri par STA_ID (colonne sta_nm_anfr)
        File file = new File(Main.ABS_PATH +"/input/SUP_ANTENNE.txt");
        InputStream is;
        try {
            is = new FileInputStream(file.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line = "";
            int haut;
            int az;
            try {
                reader.readLine();  //step over header
                while ((line = reader.readLine()) != null) {
                    String[] tokens = line.split(";");  // Split by ';'
                    String staId = tokens[0];   //STA_NM_ANFR

                    if (tokens[2].equals("17"))
                        continue;               //bloquer les FH

                    String azimuth = tokens[5];
                    String hauteur = tokens[6];
                    azimuth = azimuth.replace(",",".");  //convertir virgule en point
                    hauteur = hauteur.replace(",",".");  //convertir virgule en point
                    try {
                        haut = (int) (Double.parseDouble(hauteur) + 0.5d);  //arrondi réel
                    } catch (NumberFormatException e) {
                        haut = -1;
                    }
                    try {   //convertir les azimuths en int pour pouvoir faire un tri par ordre croissant à l'étape suivante
                        az = (int) (Double.parseDouble(azimuth) + 0.5d);  //arrondi réel
                    } catch (NumberFormatException e) {
                        az = -1;
                    }
                    dbAa.insertData_EA("", tokens[2], staId, az, "",0.0,0.0,0,0,"","",haut);
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }



        String staIdPrec = "";
        int nbWrite =0;
        String azimuths="";
        int azimuthPrec = -1;   //pour check doublons
        int haut=-1;

        ResultSet rs = dbAa.queryRs("SELECT STA_NM_ANFR, AnfrID, Haut FROM Analytica ORDER BY STA_NM_ANFR ASC, AnfrID ASC");
        try {
            while (rs.next()) {
                //System.out.println(rs.getString(1)+" "+rs.getString(2)+" "+rs.getString(3)+" ");
                String staId = rs.getString(1);   //STA_NM_ANFR

                if (!staId.equals(staIdPrec)) {
                    if (nbWrite!=0) {
                        dbAa.sql_query("INSERT INTO SUP_ANTENNE VALUES (NULL, '" + staIdPrec + "', '" + "-1" + "', '" + azimuths + "', " + haut + ")");
                    }

                    staIdPrec = staId;
                    nbWrite++;

                    haut = rs.getInt(3);
                    azimuths = "";  //reset
                    azimuthPrec=-1; //reset
                    if (rs.getInt(2)!=-1) {
                        azimuths = rs.getString(2);
                        azimuthPrec = rs.getInt(2);
                    }
                } else {
                    int azim = rs.getInt(2);
                    if (azim!=-1 && azim!=azimuthPrec) {        //continuer seulment si c'est un azimuth valide et pas doublon
                        azimuthPrec = azim;
                        if (azimuths.length()>0) {
                            azimuths += "," + azim;             //append
                        } else {
                            azimuths = String.valueOf(azim);    //set value
                        }
                    }
                }
            }
            rs.close();
            dbAa.sql_query("INSERT INTO SUP_ANTENNE VALUES (NULL, '" + staIdPrec + "', '" + "-1" + "', '" + azimuths + "', " + haut + ")"); //dernière ligne
        } catch (SQLException e) {
            e.printStackTrace();
        }
        dbAa.delete("Analytica");  //éffacement des données SQL table temportaire -> commenter pour Debug
        System.out.println(nbWrite+" lignes insérées dans SUP_ANTENNE");
    }




    // méthode unique pour la recherche de la hauteur et des azimuths
    // nouvelle version avec 1 ligne par STA_ID dans SUP_ANTENNE et FH déjà éliminés
    private void rechHautAz(String STA_NM_ANFR) {
        azimuthsDg = "";
        hauteurDg = -1;

        ResultSet rs = dbAa.queryRs("SELECT AER_NB_AZIMUT, AER_NB_ALT_BAS FROM SUP_ANTENNE WHERE STA_NM_ANFR = '"+STA_NM_ANFR+"'");
        try {
            if (rs.next()) {
                azimuthsDg= rs.getString(1);
                hauteurDg = rs.getInt(2);
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //System.out.println("azimuthDg="+azimuthsDg+" hauteurDg="+hauteurDg);
    }



    //récupérer la date de mise à jour
    //attribuer dynamiquement les colonnes
    private void setColAndGetDataset() {
        int count = 0;
        File file = new File(Main.ABS_PATH +"/input/ANFR.csv");
        InputStream is;
        try {
            is = new FileInputStream(file.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line = "";
            try {
                while ((line = reader.readLine()) != null && count<2) {
                    count++;
                    String[] tokens = line.split(";");  // Split by ';'
                    if (count==1) {
                        //ligne d'en-tête
                        for (byte i = 0; i <= tokens.length-1; i++) {
                            //System.out.println("index "+i+"=" + tokens[i]);
                            if (tokens[i].equals("adm_lb_nom"))
                                OPERAT = i;
                            else if (tokens[i].equals("sup_id"))
                                SUP_ID = i;
                            else if (tokens[i].equals("emr_lb_systeme"))
                                SYSTEME = i;
                            else if (tokens[i].equals("emr_dt") || tokens[i].equals("emr_dt_service"))
                                SERVICE = i;
                            else if (tokens[i].equals("sta_nm_dpt"))
                                C_DEPT = i;
                            else if (tokens[i].equals("code_insee"))
                                C_INSEE = i;
                            else if (tokens[i].equals("date_maj"))
                                DATE_MAJ = i;
                            else if (tokens[i].equals("sta_nm_anfr"))
                                STA_ID = i;
                            else if (tokens[i].equals("sup_nm_haut"))
                                HAUTEUR = i;
                            else if (tokens[i].equals("adr_lb_lieu"))
                                ADR_L = i;
                            else if (tokens[i].equals("adr_lb_add1"))
                                ADR_1 = i;
                            else if (tokens[i].equals("adr_lb_add2"))
                                ADR_2 = i;
                            else if (tokens[i].equals("adr_nm_cp"))
                                C_POST = i;
                            else if (tokens[i].equals("coordonnees"))
                                COORD = i;
                        }

                        // vérifier que toutes les colonne sont identifiées
                        if (OPERAT==-1|SUP_ID==-1|SYSTEME==-1|SERVICE==-1|C_DEPT==-1|C_INSEE==-1|DATE_MAJ==-1|STA_ID==-1|HAUTEUR==-1|ADR_L==-1|ADR_1==-1|ADR_2==-1|C_POST==-1|COORD==-1) {
                            System.out.println(Main.ANSI_RED+"Erreur dans l'attribution des colonnes. Arrêt."+Main.ANSI_RESET);
                            Main.writeLog(TAG+"Erreur dans l'attribution des colonnes. Arrêt");
                            OPERAT = -1;    //pour la détection de l'erreur
                        }
                    } else if (count==2) {
                        //1ère ligne de données
                        System.out.println("Dataset=" + tokens[DATE_MAJ]);
                        String splitter;
                        byte indexY,indexM=1,indexD;
                        if (tokens[DATE_MAJ].contains("/")) {
                            splitter = "/";     //FORMAT DD/MM/YYYY
                            indexY = 2;
                            indexD = 0;
                        } else {
                            splitter = "-";     //FORMAT YYYY-MM-DD
                            indexY = 0;
                            indexD = 2;
                        }
                        //String[] tokens2 = tokens[DATE_MAJ].split("-");
                        String[] tokens2 = tokens[DATE_MAJ].split(splitter);
                        if (tokens2.length>=2) {
                            try {
                                year = Integer.parseInt(tokens2[indexY]);
                                int month= Integer.parseInt(tokens2[indexM]);
                                int day  = Integer.parseInt(tokens2[indexD]);
                                LocalDate date = LocalDate.of(year, month, day);
                                week = String.valueOf(date.get(WeekFields.of(Locale.FRANCE).weekOfYear()));
                                if (week.length()==1)
                                    week = "0"+week;
                                //System.out.println("weekOfYear="+week);
                            } catch (Exception e) {
                                week = "-1";
                                System.out.println("Catch NumberFormatException");
                                Main.writeLog(TAG+"Catch NumberFormatException");
                            }
                        } else {
                            week = "-1";  //fix 22/11/21 pour bloquer le process en cas d'erreur de décodage. Le message d'érreur sera géré par la méthode parent
                        }
                    }
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    //récupéer le dernier n° de version. Mettre 1 si aucune base trouvée
    protected int readPrevious() {
        try (Stream<Path> walk = Files.walk(Paths.get(Main.ABS_PATH + "/SQL/ANFR/"), 1)) { //mettre depth à 1 pour ne pas lire le contenu des sous-dosiers
            List<String> result = walk.map(Path::toString).filter(f -> f.endsWith(".db")).collect(Collectors.toList());
            Collections.sort(result);   //trier dansd l'ordre
            if (result.size()>=1) {
                String file = "";
                for (String n : result) {
                    file = n;
                }
                //System.out.println("n="+file);
                String[] parts = file.split(Pattern.quote(File.separator));     //suppression du path
                int length = parts.length;                      //nombre de parties (c'est la denrière qui nous intéresse)
                String fileName = parts[length-1];              //nom du fichier sans le chemin (utile pour ensuite déplacer le fichier)

                String[] tokens = fileName.split("_");  // Split by '_'
                if (tokens.length==2) {
                    String[] tokens2 = tokens[1].split("\\.");    //enlever le .db
                    try {
                        return Integer.parseInt(tokens2[0]) + 1;        //incémenter de 1 le numéro par rapport à la dernière base
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;   //aucune base existante ou erreur. Set à 1
    }



    // ajoute la hauteur et les azimuths à-postiori
    private void processHautAz(String plmn) {
        int nbSta = dbAf.numberRowsQ("SELECT COUNT(DISTINCT AnfrID) AS rowcount FROM '"+plmn+"'");
        int count=0;
        ResultSet rs = dbAf.queryRs("SELECT DISTINCT AnfrID, sta_nm_anfr FROM '"+plmn+"'"); //une ligne par station
        try {
            while (rs.next()) {
                int anfrId = rs.getInt(1);
                String stationId = rs.getString(2);
                //System.out.println(anfrId+" "+stationId);

                rechHautAz(stationId);  //rechercher la hauteur et les azimuths en une seule fois pour gagner du temps. => résultats dans hauteurDg & azimuthsDg
                if (hauteurDg == -1 || plmn.equals("99999"))    //la colonne "Haut" n'existe pas dans la table 99999
                    dbDr.sql_query("UPDATE '" + plmn + "' SET Azimuth = '" + azimuthsDg + "' WHERE AnfrID = " + anfrId);    //écrire uniquement les azimuths
                else
                    dbDr.sql_query("UPDATE '" + plmn + "' SET Azimuth = '" + azimuthsDg + "', Haut = " + hauteurDg+" WHERE AnfrID = " + anfrId);
                updateProgressBar(nbSta, count);
                count++;
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println(plmn+" Terminé " + count+" supports traités                           ");
        Main.writeLog(TAG+plmn+" -> "+count+" supports traités");
    }





    /***********************************************************************************************
     *                                   Méthodes communes
     **********************************************************************************************/

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


    private void writeFile(String file, String data) {
        try {
            FileWriter fw = new FileWriter(Main.ABS_PATH + "/"+file,true); //the true will append the new data
            fw.write(data+"\n");    //appends the string to the file
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
