import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**************************************************************************************
 * Téléchargeur de base ANFR
 * Created by Tristan on 12/04/21.
 * Télécharge le CSV sur data.anfr.fr et le met dans le dossier /input sous "ANFR.csv"
 **************************************************************************************/

public class A_ANFR_Downloader {

    private String TAG = "ANFR Downl ";

    protected int lignes;
    protected int lignesPrec;


    protected A_ANFR_Downloader() {
        gestFichier();
        lignesPrec = Main.checkFileLines(Main.ABS_PATH +"/input/ANFR.csv");

        //int li = download("https://data.anfr.fr/api/records/2.0/downloadfile/format=csv&resource_id=88ef0887-6b0f-4d3f-8545-6d64c8f597da&use_labels_for_header=true");    //nouveau lien fin mars 2022
        int li = download("https://data.anfr.fr/api/records/2.0/downloadfile/format=csv&refine.generation=4G&refine.generation=5G&resource_id=88ef0887-6b0f-4d3f-8545-6d64c8f597da&use_labels_for_header=true");

        if (li < 1000) {
            System.out.println(TAG + "Erreur lors du téléchargement");
            Main.writeLog(TAG+" Erreur lors du téléchargement");
            lignes = 0;
        } else if (li >= (lignesPrec * 0.9)) {
            Main.writeLog(TAG + li+" lignes téléchargées");
            File f1 = new File(Main.ABS_PATH + "/input/ANFR.csv");   //éffacer le fichier précédent
            f1.delete();

            File oldfile = new File(Main.ABS_PATH + "/input/ANFR_DL.csv");
            File newfile = new File(Main.ABS_PATH + "/input/ANFR.csv");
            if (!oldfile.renameTo(newfile)) {
                System.out.println("Erreur lors du renommage");
                Main.writeLog(TAG + "Erreur lors du renommage");
            }

            lignes = li;    //nb de lignes. Le faire en dernier pour que le check de "lignes > 0 dans Main" puisse confirmer que tout s'est bien passé
        } else {
            lignes = 0;
            System.out.println(TAG+"Erreur: "+li+" téléchargées < "+lignesPrec+ " préc.");
            Main.writeLog(TAG+"Erreur: "+li+" téléchargées < "+lignesPrec+ " préc.");
        }

        System.out.println("Terminé: "+lignes+" lignes téléchargées !");
    }


    private void gestFichier() {
        //vérifier, créer le répertoire si inexistant
        File file = new File(Main.ABS_PATH +"/input");
        if (!file.exists()) {
            file.mkdirs();
        }

        //éffacer éventuels fichiers précédents
        File f1 = new File(Main.ABS_PATH +"/input/ANFR_DL.csv");
        f1.delete();
    }


    public int download(String link) {
        int nbInsert;
        System.out.println(TAG + "Téléchargement "+link);
        try  {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            URL url = new URL(link);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; rv:78.0) Gecko/20100101 Firefox/78.0");   //prévention erreur 403

            nbInsert = readStream(con.getInputStream());
            con.disconnect();
        } catch (Exception e) {
            nbInsert = -1;
            e.printStackTrace();
            Main.writeLog(TAG+" "+e.getMessage());
        }
        return nbInsert;
    }


    private TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    //No need to implement.
                }
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    //No need to implement.
                }
            }
    };


    private int readStream(InputStream in) {
        int nbInsert = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String nextLine;
            while ((nextLine = reader.readLine()) != null) {
                nbInsert++;
                writeFile("ANFR_DL.csv", nextLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return nbInsert;
    }






    /***********************************************************************************************
     *                                   Méthodes communes
     **********************************************************************************************/

    private void writeFile(String file, String data) {
        try {
            FileWriter fw = new FileWriter(Main.ABS_PATH +"/input/"+file,true);
            fw.write(data+"\n");    //appends the string to the file
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
