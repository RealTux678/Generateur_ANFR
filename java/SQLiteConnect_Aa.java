import java.sql.*;

/**
 * Created by Tristan on 28/10/19.
 */

public class SQLiteConnect_Aa {
    private String TAG = "SQLiteConnex: ";
    private String dbPath = Main.ABS_PATH +"/SQL/SQLite_Aa.db";
    private Connection connection = null;
    private Statement statement = null;

    public SQLiteConnect_Aa() {
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Error at SQL connect()");
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
    public void saveFile() {
        try {
            Statement stmt = this.connection.createStatement();
            stmt.executeUpdate("backup to '"+dbPath+"'");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
            System.out.println(e.getMessage());
        }
    }


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




    //renvoie la commune d'un enregistrement
    public String getCommune(String insee) {
        String city = "Commune";
        try {
            Statement stt = this.connection.createStatement();
            ResultSet rs = stt.executeQuery( "SELECT * FROM laposte WHERE INSEE = '"+ insee + "'");
            if (!rs.isClosed()) {
                city = rs.getString("Commune");
                rs.close();
            }
            stt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return city;
    }



    public void delete(String table) {
        String query = "DELETE FROM '"+table+"'";
        String quer2 = "DELETE FROM SQLITE_SEQUENCE WHERE NAME = '"+table+"'";
        try {
            Statement stt = connection.createStatement();
            stt.executeUpdate(query);   //éffacement contenu de la table
            stt.executeUpdate(quer2);   //reset du ID
            stt.close();                //fermer le curseur
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    //compter le nombre de lignes
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



}
