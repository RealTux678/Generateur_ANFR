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
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**************************************************************************************
 * Générateur de bases ANFR eNB Analytics -> version simplifiée pour Cartotelco
 * Updated by Tristan on 12/04/21, 12/10/21, 24/11/23.
 *
 * Pré-requis: "ANFR.csv" et "laposte_hexasmal.csv" dans le dossier input
 * Génère: ANFR/yyyy-Sww_Ver.db : base SQL 1l/Freq pour base et suivi de l'historique
 *************************************************************************************/

public class A_Generateur_ANFR_Cartotelco {

    private String TAG = "ANFR Gener ";
    private SQLiteConnexion dbAa;                   //base SQL de travail (volatile)
    private SQLiteConnexion dbHt;                   //base SQL ANFR "histo" générée

    private int year;                               //Année
    private String week = "-1";                     //n° de  semaine. Initialiser à -1 car 0 est conforme (fin de la semaine 53 début janvier). Utiliser type String pour pouvoir rajouter un 0 pour les semaines 1 à 9
    public String dbFile = "";                      //nom du fichier SQL "Ht" contenant la version. Sera utilié pour recharger la bonne base en POST

    private int[] csvIndexes = new int[14];         //tableau contenant les indexes de colonnes du CSV, sera initialisé à -1 pour pouvoir détecter une erreur dans l'auto-attribution



    protected A_Generateur_ANFR_Cartotelco() {
        dbAa = new SQLiteConnexion();
        dbAa.connect();
        dbAa.sql_query("CREATE TABLE Analytica (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, STA_NM_ANFR TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT, Haut SMALLINT)");
        dbAa.sql_query("CREATE TABLE laposte (ID INTEGER PRIMARY KEY AUTOINCREMENT, INSEE TEXT, Commune TEXT)");
        dbAa.sql_query("CREATE TABLE SUP_ANTENNE (ID INTEGER PRIMARY KEY AUTOINCREMENT, STA_NM_ANFR TEXT, TAE_ID INTEGER, AER_NB_AZIMUT TEXT, AER_NB_ALT_BAS SMALLINT)"); //pour la hauteur réelle de la station


        // PASSE UNIQUE (Cartotelco)
        dbHt = new SQLiteConnexion();
        dbHt.dbPath = Main.ABS_PATH + File.separator+"SQL"+File.separator+"ANFR"+File.separator;    //override path
        dbHt.connect();
        dbHt.sql_query("CREATE TABLE '20801' (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        dbHt.sql_query("CREATE TABLE '20810' (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        dbHt.sql_query("CREATE TABLE '20815' (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        dbHt.sql_query("CREATE TABLE '20820' (ID INTEGER PRIMARY KEY AUTOINCREMENT, CP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        dbHt.sql_query("CREATE TABLE '99999' (ID INTEGER PRIMARY KEY AUTOINCREMENT, OP TEXT, INSEE TEXT, sta_nm_anfr TEXT, AnfrID INTEGER, AnfrData TEXT, LAT REAL, LON REAL, xG TINYINT, Act TINYINT, DateAct TEXT, Syst TEXT)");
        dbHt.sql_query("CREATE TABLE Version (ID INTEGER PRIMARY KEY AUTOINCREMENT, Version INTEGER, Date TEXT)");

        //gestion des fichiers et checks préalables
        //year et week sont positionnés dans gestFichier()
        if (gestFichier()) {
            int anfrVersion = readPrevious();   // lire la base ANFR précédente, récupérer le n° de version et incrémenter
            System.out.println("Générateur ANFR -> "+week+" "+year+" ["+anfrVersion+"]");
            Main.writeLog("Générateur ANFR -> "+week+" "+year+" ["+anfrVersion+"]");

            dbHt.sql_query("INSERT INTO Version VALUES (NULL, " + anfrVersion + ", 'S"+week+" "+year+"')");

            communes2db();          //envoyer les communes dans la base SQL

            readAnfr("20801", "ORANGE", false);
            readAnfr("20810", "SFR", false);
            readAnfr("20815", "FREE MOBILE", false);
            readAnfr("20820", "BOUYGUES TELECOM", false);
            readAnfr("BPT", "BPT/SPT", true);
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
            dbHt.saveFile(this.dbFile);     //écrire le fichier pour pouvoir lancer le process diff
            dbHt.close();
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
        if (lignes < 1000) {
            System.out.println(Main.ANSI_RED+"Erreur : ANFR.csv nombre de lignes incorrect"+Main.ANSI_RESET);
            Main.writeLog(TAG+"Erreur : ANFR.csv nombre de lignes incorrect");
            return false;
        }

        setColAndGetDataset();  //récupérer la date de MAJ et définir dynamiquement les colonnes
        if (csvIndexes[0] == -1)
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

        return true;
    }



    //envoyer les communes dans la table SQL "laposte"
    private void communes2db() {
        File file = new File(Main.ABS_PATH+File.separator+"input"+File.separator+"laposte_hexasmal.csv");
        try {
            InputStream is = new FileInputStream(file.getAbsolutePath());
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

        // Entrée : fichier "ANFR.csv" dans le dossier ../input
        // Sortie : Base SQL, table ANFR
        File file = new File(Main.ABS_PATH+File.separator+"input"+File.separator+"ANFR.csv");
        try {
            InputStream is = new FileInputStream(file.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line = "";
            String anfrData;
            String anfrAdresse1, anfrAdresse2, anfrAdresseLieu, cPostal;
            int step1Read=1,step2wrtite = 0;
            double anfrLat, anfrLon;
            int xg, act;
            String separator = ";";

            try {
                reader.readLine();  //step over header

                while ((line = reader.readLine()) != null) {
                    if (step1Read==1) {
                        // détecter le séparateur
                        String[] test = line.split(separator);
                        if (test.length==1) {
                            System.out.println("Changement séparateur vers ','");
                            separator = ",";
                        }
                    }

                    step1Read++;
                    //System.out.println(TAG+ "line="+line);
                    //String[] tokens = line.split(separator);  //split by ';'
                    String[] tokens = line.split(separator+"(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);  //regex magique
                    String systeme = tokens[csvIndexes[2]];

                    if (tokens[csvIndexes[0]].equals(opLong) && (systeme.contains("LTE") || systeme.contains("NR"))) {

                        if (systeme.contains("NR"))
                            xg = 5;
                        else
                            xg = 4;

                        // *** détection antennes inactives ***
                        if (!tokens[csvIndexes[3]].equals(""))
                            act = 1;
                        else
                            act = 0;

                        String anfrLatLon = tokens[csvIndexes[13]];
                        anfrLatLon = anfrLatLon.replace("\"", "");  //supprimer d'éventuels "  IMPORTANT (sinon le fichier JS sera corrompu)
                        //Découpage du champ coordonnées
                        String[] parts = anfrLatLon.split(", ");
                        try {
                            anfrLat = Double.parseDouble(parts[0]);    //converstion String en double
                            anfrLon = Double.parseDouble(parts[1]);
                        } catch (Exception e) {
                            // ArrayIndexOutOfBoundsException ou NumberFormatException
                            System.out.println(tokens[csvIndexes[13]]);
                            System.out.println(Main.ANSI_RED+"Erreur coordonnées GPS: \""+anfrLatLon+ "\" ligne "+step1Read+Main.ANSI_RESET);
                            continue;   //ne pas aller plus loin si coordonnées inexploitables. rajout 02/08/21
                        }

                        // arrondi (réel) à 4 décimales.
                        // important pour limiter le poids du fichier de représentation géographique
                        anfrLat = Math.round(anfrLat * 10000) / 10000.0;
                        anfrLon = Math.round(anfrLon * 10000) / 10000.0;

                        cPostal = tokens[csvIndexes[12]];
                        anfrAdresse1 = tokens[csvIndexes[10]];
                        anfrAdresse2 = tokens[csvIndexes[11]];
                        anfrAdresseLieu = tokens[csvIndexes[9]];

                        //supprimer d'éventuels "
                        anfrAdresse1 = anfrAdresse1.replace("\"", "");
                        anfrAdresse1 = anfrAdresse1.trim();     //fix 8/02/26 certains champs "Adresse1" sont remplis d'espaces
                        anfrAdresse2 = anfrAdresse2.replace("\"", "");
                        anfrAdresse2 = anfrAdresse2.trim();     //fix 8/02/26 certains champs "Adresse2" sont remplis d'espaces
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

                        //supprimer le texte et ne garder que la fréquence
                        //systeme = systeme.replace("GSM ", "");  //gestion de la fréquence
                        //systeme = systeme.replace("LTE ", "");  //gestion de la fréquence
                        //systeme = systeme.replace(" Expe", "");  //gestion de la fréquence
                        systeme = systeme.replace("5G NR ", "");  //gestion de la fréquence
                        systeme = systeme.replaceAll("[^\\d.]", "");  //garder uniquement les caractères numériques

                        String[] tok = tokens[csvIndexes[3]].split("T");    //supprimer le 'T00:00:00'
                        String service = tok[0];

                        anfrData = anfrData.replace("'", "''");  //escape single quote before SQL insert !

                        // insert SQL
                        int departement;
                        try {
                            departement = Integer.parseInt(tokens[csvIndexes[4]]);
                        } catch (NumberFormatException e) {
                            departement = 20;    //Corse
                        }

                        //hauteur. Attention ici c'est la hauteur du pylone et non celle de l'antenne
                        tokens[csvIndexes[8]] = tokens[csvIndexes[8]].replace("\"", "");    //supprimer d'éventuels "
                        tokens[csvIndexes[8]] = tokens[csvIndexes[8]].replace(",", ".");    //remplacer éventuelle virgule
                        double haut = Double.parseDouble(tokens[csvIndexes[8]]);
                        if (haut > 70.0)
                            haut = 70.0;
                        int hauteur = (int) (haut+0.5d);

                        if (!isOm && departement<96 || isOm && departement>95) {    //utile car Orange est présent dans les 2 cas
                            dbAa.insertData_EA(cPostal, tokens[csvIndexes[5]], tokens[csvIndexes[7]], Integer.parseInt(tokens[csvIndexes[1]]), anfrData, anfrLat, anfrLon, xg, act, service, systeme, hauteur);
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
        String anfrDataPrec = "";
        String stationId;
        String systemes5= "";
        double latPrec, lonPrec;
        int xg, actPrec;

        int lignes = dbAa.numberRows("Analytica");
        try {
            ResultSet rs = dbAa.queryRs("SELECT * FROM Analytica ORDER BY AnfrID ASC, xG ASC, Act DESC");
            while (rs.next()) {
                updateProgressBar(lignes, read);
                read++;
                anfrId = rs.getInt(5);

                if (anfrId != anfrIdPrec) {
                    // nouvelle ligne différente

                    if (anfrIdPrec > 0) {
                        step2write++;
                    }

                    anfrIdPrec = anfrId;  // nouvelle réf
                    xg = rs.getInt(9);
                    anfrDataPrec = rs.getString(6);

                    String insee = rs.getString(3);
                    if (insee.length()==4)
                        insee = "0"+insee;        // sur les dépts. 1 à 9 le 0x manque. Anciens open data uniquemenrt

                    String commune = dbAa.getSqlString("SELECT Commune FROM laposte WHERE INSEE = '"+ insee + "'", "Commune");  //faire la recherche de commune dans cette étape sinon on risque de recherche plusieurs fois la même commune
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
                        dbHt.sql_query("INSERT INTO '99999' VALUES (NULL, '"+oper+"', '"+rs.getString(3)+"', '"+stationId+"', "+anfrId+", '"+anfrDataSql+"', '"+latPrec+"', '"+lonPrec+"', "+xg+", "+actPrec+", '"+rs.getString(11)+"', '"+rs.getString(12)+"')");
                    else
                        dbHt.sql_query("INSERT INTO '"+oper+"' VALUES (NULL, '"+rs.getString(2)+"', '"+rs.getString(3)+"', '"+stationId+"', "+anfrId+", '"+anfrDataSql+"', '"+latPrec+"', '"+lonPrec+"', "+xg+", "+actPrec+", '"+rs.getString(11)+"', '"+rs.getString(12)+"')");
                } else {
                    xg = rs.getInt(9);
                    String anfrDataSql = anfrDataPrec.replace("'","''");  //escape single quote before SQL insert !
                    if (isOm)
                        dbHt.sql_query("INSERT INTO '99999' VALUES (NULL, '"+oper+"', '"+rs.getString(3)+"', '"+rs.getString(4)+"', "+anfrId+", '"+anfrDataSql+"', '"+rs.getDouble(7)+"', '"+rs.getDouble(8)+"', "+xg+", "+rs.getInt(10)+", '"+rs.getString(11)+"', '"+rs.getString(12)+"')");
                    else
                        dbHt.sql_query("INSERT INTO '"+oper+"' VALUES (NULL, '"+rs.getString(2)+"', '"+rs.getString(3)+"', '"+rs.getString(4)+"', "+anfrId+", '"+anfrDataSql+"', '"+rs.getDouble(7)+"', '"+rs.getDouble(8)+"', "+xg+", "+rs.getInt(10)+", '"+rs.getString(11)+"', '"+rs.getString(12)+"')");
                }
            }

            step2write++;

            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println (oper+" Step 2 : "+step2write+" Sup.                                  ");
        Main.writeLog(TAG+oper+" -> "+step2write+" supports");
    }




    //récupérer la date de mise à jour
    //attribuer dynamiquement les colonnes
    private void setColAndGetDataset() {
        int count = 0;
        File file = new File(Main.ABS_PATH +"/input/ANFR.csv");
        try {
            InputStream is = new FileInputStream(file.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String separator = ";";
            String line = "";
            try {
                while ((line = reader.readLine()) != null && count<2) {
                    if (count==0) {
                        // détecter le séparateur
                        String[] test = line.split(separator);
                        if (test.length==1) {
                            System.out.println("Changement séparateur vers ','");
                            separator = ",";
                        }
                    }

                    count++;
                    String[] tokens = line.split(separator);  // Split by ';'

                    if (count==1) {
                        //ligne d'en-tête
                        Arrays.fill(csvIndexes, -1);  //tout mettre à -1 pour pouvoir détecter une erreur d'attribution

                        for (byte i = 0; i <= tokens.length-1; i++) {
                            //System.out.println("index "+i+"=" + tokens[i]);
                            if (tokens[i].equals("adm_lb_nom")||tokens[i].equals("Opérateur"))
                                csvIndexes[0] = i;
                            else if (tokens[i].equalsIgnoreCase("sup_id"))
                                csvIndexes[1] = i;
                            else if (tokens[i].equals("emr_lb_systeme")||tokens[i].equals("Système"))
                                csvIndexes[2] = i;
                            else if (tokens[i].contains("emr_dt")||tokens[i].equals("Mise en service déclaré"))  //emr_dt ou emr_dt_service
                                csvIndexes[3] = i;  //SERVICE
                            else if (tokens[i].equalsIgnoreCase("sta_nm_dpt"))
                                csvIndexes[4] = i;  //C_DEPT
                            else if (tokens[i].equalsIgnoreCase("code_insee"))
                                csvIndexes[5] = i;  //C_INSEE
                            else if (tokens[i].equalsIgnoreCase("date_maj"))
                                csvIndexes[6] = i;  //DATE_MAJ
                            else if (tokens[i].equals("sta_nm_anfr"))
                                csvIndexes[7] = i;  //STA_ID
                            else if (tokens[i].equals("sup_nm_haut"))
                                csvIndexes[8] = i;  //HAUTEUR
                            else if (tokens[i].equals("adr_lb_lieu")||tokens[i].equals("Adresse"))
                                csvIndexes[9] = i;  //ADR_L
                            else if (tokens[i].equals("adr_lb_add1")||tokens[i].equals("Adresse 1"))
                                csvIndexes[10] = i; //ADR_1
                            else if (tokens[i].equals("adr_lb_add2")||tokens[i].equals("Adresse 2"))
                                csvIndexes[11] = i; //ADR_2
                            else if (tokens[i].equals("adr_nm_cp")||tokens[i].equals("Code postal"))
                                csvIndexes[12] = i; //C_POST
                            else if (tokens[i].equals("coordonnees"))
                                csvIndexes[13] = i; //COORD
                        }

                        // vérifier que toutes les colonnes sont identifiées
                        if (IntStream.of(csvIndexes).anyMatch(x -> x == -1)) {
                            System.out.println(TAG + "h="+ Arrays.toString(csvIndexes));
                            System.out.println(Main.ANSI_RED + "Erreur dans l'attribution des colonnes. Arrêt." + Main.ANSI_RESET);
                            Main.writeLog(TAG+"Erreur dans l'attribution des colonnes. Arrêt.");
                            csvIndexes[0] = -1;    //pour la détection de l'erreur
                        }
                    } else if (count==2) {
                        //1ère ligne de données
                        System.out.println("Dataset=" + tokens[csvIndexes[6]]);
                        String splitter;
                        byte indexY, indexM=1, indexD;
                        if (tokens[csvIndexes[6]].contains("T")) {
                            String[] tk = tokens[csvIndexes[6]].split("T");
                            tokens[csvIndexes[6]] = tk[0];
                        }

                        if (tokens[csvIndexes[6]].contains("/")) {
                            splitter = "/";     //FORMAT DD/MM/YYYY
                            indexY = 2;
                            indexD = 0;
                        } else {
                            splitter = "-";     //FORMAT YYYY-MM-DD
                            indexY = 0;
                            indexD = 2;
                        }
                        //String[] tokens2 = tokens[DATE_MAJ].split("-");
                        String[] tokens2 = tokens[csvIndexes[6]].split(splitter);
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
                                e.printStackTrace();
                                week = "-1";
                                Main.writeLog(TAG+"Catch NumberFormatException");
                            }
                        } else {
                            week = "-1";  //fix 22/11/21 pour bloquer le process en cas d'erreur de décodage. Le message d'érreur sera géré par la méthode parent
                        }
                    }
                }
                reader.close();
            } catch (IOException e) {
                System.out.println("Erreur reading date on line " + line);
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    //récupéer le dernier n° de version. Mettre 1 si aucune base trouvée
    protected int readPrevious() {
        try (Stream<Path> walk = Files.walk(Paths.get(Main.ABS_PATH+File.separator+"SQL"+File.separator+"ANFR"+File.separator), 1)) { //mettre depth à 1 pour ne pas lire le contenu des sous-dosiers
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


}
