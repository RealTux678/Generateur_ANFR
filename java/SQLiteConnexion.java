import java.io.File;
import java.sql.*;

/**
 * Gestionnaire SQLite générique
 * -----------------------------
 * Created by Tristan on 26/09/19.
 * Updated by Tristan on 11/03/22. (version générique)
 */

public class SQLiteConnexion {

    private String TAG = "SQLiteConnect ";
    protected String dbPath = Main.ABS_PATH + File.separator + "SQL" + File.separator;
    private Connection connection = null;
    private Statement statement = null;


    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite::memory:");   //fonct. en RAM
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    public void restore(String dbFile) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite::memory:");   //fonct. en RAM
            statement = connection.createStatement();
            statement.execute("restore from '"+ dbPath + dbFile +"'");     //charger le fichier SQL existant
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Error at SQL restore()");
            e.printStackTrace();
        }
    }


    public void close() {
        try {
            statement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    //ECRIRE LE FICHIER
    public void saveFile(String dbFile) {
        File fdb = new File(dbPath + dbFile);
        fdb.delete();
        try {
            Statement stmt  = this.connection.createStatement();
            stmt.executeUpdate("backup to '"+ dbPath + dbFile+"'");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized Connection getConnection() {
        return connection;
    }

    public ResultSet queryRs(String request) {
        ResultSet resultSet = null;
        try {
            Statement stt = connection.createStatement();
            resultSet = stt.executeQuery(request);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resultSet;
    }


    public void sql_query(String query) {
        try {
            Statement stt = connection.createStatement();
            stt.executeUpdate(query);
            stt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void delete(String table) {
        String query = "DELETE FROM '"+table+"'";
        String quer2 = "DELETE FROM SQLITE_SEQUENCE WHERE NAME = '"+table+"'";
        try {
            Statement stt = connection.createStatement();
            stt.executeUpdate(query);   //éffacement contenu de la table
            stt.executeUpdate(quer2);   //reset du ID
            stt.close();        //fermer le curseur
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public ResultSet getRow(String table, int id) {
        ResultSet rs = null;
        try {
            rs = statement.executeQuery( "SELECT * FROM '" + table + "' WHERE ID = "+ id +";");
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Erreur dans la requete ");
        }
        return rs;
    }


    //éffacer une ligne spécifique
    public void deleteRow(String table, int id) {
        String sql = "DELETE FROM '" + table + "' WHERE id = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, id);    //set the corresponding param
            pstmt.executeUpdate();     //execute the delete statement
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }


    //compter le nombre de lignes d'une table
    public int numberRows(String table) {
        int count = 0;
        String query = "SELECT COUNT(*) AS rowcount FROM '" + table + "'";
        try {
            Statement stt = connection.createStatement();
            ResultSet rs = stt.executeQuery(query);
            rs.next();
            count = rs.getInt("rowcount");
            rs.close();
            stt.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return count;
    }


    //compter le nombre de lignes avec requête libre
    public int numberRowsQ(String query) {
        int count = 0;
        try {
            Statement stt = connection.createStatement();
            ResultSet rs = stt.executeQuery(query);
            rs.next();
            count = rs.getInt("rowcount");
            rs.close();
            stt.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return count;
    }



    // vérifie qu'une table existe
    public boolean tableExists(String tableName) {
        try {
            DatabaseMetaData dbm = connection.getMetaData();
            ResultSet tables = dbm.getTables(null, null, tableName, null);
            return tables.next();
        } catch(SQLException ex) {
            System.out.println(ex.getMessage());
        }
        return false;
    }


    //renvoie le texte d'une cellule précise
    public String getSqlString(String query, String row) {
        String returnVal = "";
        try {
            Statement stt = this.connection.createStatement();
            ResultSet rs = stt.executeQuery( query);
            if (!rs.isClosed()) {
                returnVal = rs.getString(row);
                rs.close();     //fermer le curseur
            }
            stt.close();        //fermer le statement
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return returnVal;
    }





    /***********************************************************************************************
     *                        Fonctions spécifiques à éliminer
     **********************************************************************************************/

    public void insertData_EA(String cp, String insee, String sta, int AnfrID, String AnfrData, double LAT, double LON, int xg, int act, String actDate, String syst, int haut) {
        String query;
        query = "INSERT INTO Analytica VALUES (NULL, '"+cp+"', '"+insee+"', '"+sta+"', "+AnfrID+", '"+AnfrData+"', '"+LAT+"', '"+LON+"', "+xg+", "+act+", '"+actDate+"', '"+syst+"', "+haut+")";
        try {
            Statement stt = connection.createStatement();
            stt.executeUpdate(query);
            stt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    //compter le nombre de lignes
    public int numberRowsW(String table, String where) {
        int count = 0;
        String query = "SELECT COUNT(*) AS rowcount FROM '" + table + "' WHERE "+where;
        try {
            Statement stt = connection.createStatement();
            ResultSet rs = stt.executeQuery(query);
            rs.next();
            count = rs.getInt("rowcount");
            rs.close();
            stt.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return count;
    }



}
