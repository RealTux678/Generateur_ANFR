import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Tristan on 3/01/20.
 */

public class Main {
    public static String version = "1.27 [Mar 18 2022]";

    // PATHS
    public static String ABS_PATH, F_PATH;
    public final static String SERVER_FILES_PATH = "/var/www/html";
    
    public static boolean DEBUG;    //mode debug
    

    public static void main(String[] args) {
        long startTime = timestampMilis();
        System.out.println("Tower Utils "+version);

        setPaths();

        if (args.length==0) {
            start();                            //pas d'argument -> mode debug
        } else if (args[0].equalsIgnoreCase("anfr")) {
            // Process normal
            System.out.println("ANFR");
            A_ANFR_Downloader dw = new A_ANFR_Downloader();
            if (dw.lignes > 0) {
                new A_Generateur_ANFR_Cartotelco("");
                new B_Generateur_ANFR_Diff();
                //new G_Copy_To_Server("DIFF");
            }
        } else if (args[0].equalsIgnoreCase("anfr-local")) {
            // Mode sans téléchargement. Suppose que le fichier ANFR.csv a été placé manuellement dans /input
            new A_Generateur_ANFR_Cartotelco(""); 
            new B_Generateur_ANFR_Diff();
            //new G_Copy_To_Server("DIFF");
        } else if (args[0].equalsIgnoreCase("anfr-diff-only")) {
            // Générer seulement la carte des différences
            new B_Generateur_ANFR_Diff();
            //new G_Copy_To_Server("DIFF");
        }
        System.out.println("Done");
    }


    //MODE DEBUG
    private static void start() {
        new A_ANFR_Downloader();
        new A_Generateur_ANFR_Cartotelco("");
        new B_Generateur_ANFR_Diff();
    }



    // Couleurs console
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_BLUE = "\u001B[34m";



    // Récupère la date au format unix (milisecondes)
    public static long timestampMilis() {
        return System.currentTimeMillis();
    }

    // Conversion date UNIX en date lisible
    public static String epoch2Date(long epochMilis) {
        Date updatedate = new Date(epochMilis*1000);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        return dateFormat.format(updatedate);
    }

    // compte le nombre de lignes dans un fichier texte
    public static int checkFileLines(String path) {
        File file = new File(path);
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            int lineCount = 0;
            while ((br.readLine()) != null) {
                lineCount++;
            }
            //System.out.println("lignes de "+path+" : "+String.valueOf(lineCount));
            br.close(); //fermer le flux.
            return lineCount;
        } catch (IOException e) {
            System.out.println(ANSI_RED + e.getMessage() + ANSI_RESET);
        }
        return -1;
    }




    /***********************************************************************************************
     *                                       $ PATH
     **********************************************************************************************/

    //définit les chemins d'accès aux fichiers
    private static void setPaths() {

        File file = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()); //racine du main, mais ne fonctionne pas si lancé dans IntelliJ
        ABS_PATH = file.getAbsolutePath();      // chemin absolu

        if (ABS_PATH.contains("out"+ File.separator+ "production")) {
            // INTELLIJ MODE
            DEBUG = true;
            File fp = new File("");
            ABS_PATH = fp.getAbsolutePath();    // chemin absolu, méthode normale, mais ne fonctionne pas si le shell n'est pas dans le dossier, mais IntelliJ OK
        }
        File file2 = new File(ABS_PATH);
        F_PATH = file2.getParent();             // dossier parent
    }



    /***********************************************************************************************
     *                                        SYSLOG
     **********************************************************************************************/

    // Obtenir la date
    public static String dateLog() {
        Date updatedate = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(updatedate);
    }

    // Obtenir l'heure
    public static String timeLog() {
        Date updatedate = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        return dateFormat.format(updatedate);
    }

    public static void writeLog(String data) {
        File file = new File(Main.ABS_PATH +"/log");
        if (!file.exists()) {
            file.mkdirs();
        }

        try {
            FileWriter fw = new FileWriter(ABS_PATH +"/log/"+dateLog()+".log",true);
            fw.write(timeLog()+" "+data+"\n");
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
