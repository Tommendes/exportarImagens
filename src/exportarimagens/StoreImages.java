/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package exportarimagens;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

/**
 *
 * @author TomMe
 */
public class StoreImages {

    // O endereço da base de dados
    private static final String URL = "jdbc:firebirdsql:localhost/3050:" + System.getProperty("user.dir") + "\\MGFP01.fdb?charSet=utf-8";
    private static final String USER = "sysdba";
    private static final String PASSWORD = "masterkey";
    private static final String DEFAULT_FOLDER = System.getProperty("user.dir") + "\\imagens";//"F:\\TomMe\\Downloads\\Mega\\MGFolha\\imagens";

    private final Path targetFolder;

    private Connection connection;

    public StoreImages(String targetFolder) {
        this.targetFolder = Paths.get(targetFolder);
    }

    /**
     * Exporta as imagens do BD para uma pasta de imagens no root da aplicação
     *
     * @throws IOException
     * @throws SQLException
     */
    public void store() throws IOException, SQLException {
        createDir(DEFAULT_FOLDER);
        if (!Files.isDirectory(targetFolder)) {
            throw new FileNotFoundException(String.format("The folder %s does not exist", targetFolder));
        }
        try (
                Statement stmtcontar = this.getConnection().createStatement();
                ResultSet rs = stmtcontar.executeQuery("select count(*) as quant from servidores where b_foto is not null")) {
            if (rs.next()) {
                Principal.getpBarAtual().setMaximum(rs.getInt("quant"));
                Principal.getpBarAtual().setValue(0);
            }
        }

        try (
                Statement stmt = this.getConnection().createStatement();
                ResultSet rs = stmt.executeQuery("select (idservidor||'.jpg') as idservidor, b_foto from servidores where b_foto is not null")) {
            while (rs.next()) {
                final Path targetFile = targetFolder.resolve(rs.getString("idservidor"));
                if (Files.exists(targetFile)) {
                    System.out.printf("File %s already exists%n", targetFile);
                    mensagem("File %s already exists%n " + targetFile.toString());
                    Files.delete(targetFile);
//                    continue;
                }
                try (InputStream data = rs.getBinaryStream("b_foto")) {
//                    Exporta a imagem
                    Files.copy(data, targetFile);

                    mensagem("Imagem exportada com sucesso: " + rs.getString("idservidor"));
//                    Cria imagem em memória para ser reduzida
                    File imagemOriginal = new File(targetFile.toString());
                    BufferedImage imagemReduzida = ImageIO.read(imagemOriginal);
                    imagemReduzida.getScaledInstance(64, 64, 10);
                    Files.delete(targetFile);
                    if (ImageIO.write(imagemReduzida, "jpg", new File(targetFile.toString()))) {
                        mensagem("Imagem reduzida com sucesso: " + rs.getString("idservidor"));
                    }
                }
                atualizaBarras();
            }
        } catch (SQLException e) {
            mensagem("Houve um erro ao localizar os registros na tabela. Erro: " + e.getMessage());
        }
    }

    public void alterarTabelaServidores() {
        try {
            String sql = "ALTER TABLE SERVIDORES DROP B_FOTO";
            PreparedStatement ps2 = this.getConnection().prepareStatement(sql);
            ps2.executeUpdate();
        } catch (SQLException e) {
            mensagem("Houve um erro ao alterar a tabela. Erro: " + e.getMessage());
        } finally {
            InserirOpcao("Sucesso", "ALTER TABLE SERVIDORES DROP B_FOTO");
            atualizaBarras();
            mensagem("Tabela servidores alterada com sucesso.");
            Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
        }
    }

    /**
     * Insere uma informação na tabela de opções do sistema
     *
     * @param chave
     * @param opcao
     */
    public void InserirOpcao(String chave, String opcao) {
        try {
            String sql = "insert into opcoes values "
                    + "((select count(id)+1 from opcoes),?,?)";
            PreparedStatement ps1 = this.getConnection().prepareStatement(sql);
            ps1.setString(1, chave);
            ps1.setString(2, opcao);
            ps1.executeUpdate();
        } catch (SQLException e) {
            mensagem("Erro: " + e.getMessage());
        }
    }

    /**
     * Conectar ao BD
     */
    public void conectar() {
        try {
            mensagem("Conectar");
            atualizaBarras();
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            this.setConnection(conn);
        } catch (SQLException e) {
            mensagem("Erro: " + e.getMessage());
        } finally {
            mensagem("Conectado");
            Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
        }
    }

    /**
     * Desconectar ao BD
     */
    public void desconectar() {
        try {
            mensagem("Desconectar");
            atualizaBarras();
            if (!this.getConnection().isClosed()) {
                this.getConnection().close();
            }
        } catch (SQLException e) {
            mensagem("Erro: " + e.getMessage());
        } finally {
            mensagem("Desconectado");
            Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
        }
    }

    public void criarTblOpcao() {
        try {
            mensagem("Verificar tabela de opções");
            atualizaBarras();
            try (
                    Statement stmt = this.getConnection().createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT RDB$RELATION_NAME as TABELA FROM RDB$RELATIONS WHERE RDB$FLAGS=1 and RDB$RELATION_NAME='OPCOES'");) {
                if (!rs.next()) {
                    try {
                        mensagem("Tabela não existe.");
                        mensagem("Criar tabela de opções");
                        String sql = "CREATE TABLE OPCOES (ID INTEGER DEFAULT 0 NOT NULL,chave VARCHAR(50),opcao VARCHAR(255));";
                        PreparedStatement ps2 = this.getConnection().prepareStatement(sql);
                        ps2.executeUpdate();
                    } catch (SQLException e) {
                        mensagem("Houve um erro ao criar a tabela. Erro: " + e.getMessage());
                    } finally {
                        mensagem("Tabela de opções criada com sucesso");
                    }
                } else {
                    mensagem("Tabela de opções ok");
                }
                Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
            } catch (SQLException e) {
                mensagem("Houve um erro ao criar a tabela. Erro: " + e.getMessage());
            } finally {
                mensagem("Tabela de opções criada com sucesso");
            }
        } catch (Exception e) {
            mensagem("Houve um erro ao criar a tabela. Erro: " + e.getMessage());
        }
    }

    /**
     * Cria a tabela de sessões de usuário caso não exista e a seguir insere um
     * usuário padrão que não será excluído pelo MGFolha e portanto a sessão
     * nunca estará vazia provocando o MGFolha a nao codificar o BD na saída
     */
    public void desativaCodif() {
        try {
            atualizaBarras();
            try (
                    Statement stmt = this.getConnection().createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT RDB$RELATION_NAME as TABELA FROM RDB$RELATIONS WHERE RDB$FLAGS=1 and RDB$RELATION_NAME='SESSOES'");) {
                if (!rs.next()) {
                    try {
                        mensagem("Tabela sessões não existe.");
                        mensagem("Criar tabela de sessoes");
                        String sql = "CREATE TABLE SESSOES (ID INTEGER DEFAULT 0 NOT NULL,COMPUTADOR VARCHAR(100),USUARIO VARCHAR(100));";
                        PreparedStatement ps2 = this.getConnection().prepareStatement(sql);
                        ps2.executeUpdate();
                    } catch (SQLException e) {
                        mensagem("Houve um erro ao criar a tabela e desativar a verificação inicial. Erro: " + e.getMessage());
                    } finally {
                        mensagem("Tabela de sessoes criada com sucesso");
                    }
                } else {
                    mensagem("Tabela de sessoes ok");
                }
                Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
            } catch (SQLException e) {
                mensagem("Houve um erro ao consultar a tabela de sessoes. Erro: " + e.getMessage());
            }

            try {
                mensagem("Desativando verificação inicial");
                String sql = "DELETE FROM SESSOES;";
                PreparedStatement ps3 = this.getConnection().prepareStatement(sql);
                ps3.executeUpdate();
                sql = "INSERT INTO sessoes VALUES(1,'MGFOLHA','MGFOLHA');";
                PreparedStatement ps4 = this.getConnection().prepareStatement(sql);
                ps4.executeUpdate();
            } catch (SQLException e) {
                mensagem("Houve um erro ao desativar a verificação inicial. Erro: " + e.getMessage());
            } finally {
                mensagem("Verificação inicial desativada com sucesso");
            }
        } catch (Exception e) {
            mensagem("Houve um erro ao criar a tabela. Erro: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public String mensagem(String texto) {
        String ret = texto;
        System.out.println(ret);
        Principal.getjTextArea1().append(ret + "\n");
        Principal.getjTextArea1().setCaretPosition(Principal.getjTextArea1().getText().length());
        return ret;
    }

    public static void atualizaBarras() {
        Principal.getpBarAtual().setValue(Principal.getpBarAtual().getValue() + 1);
        Principal.getpBarTotal().setValue(Principal.getpBarTotal().getValue() + 1);
        Principal.getpBarTotal().setString("Progresso total...");
    }

    public void createDir(String folder) {
        File theDir = new File(folder);

        if (!theDir.exists()) {
            mensagem("creating directory: " + theDir.getName());
            boolean result = false;

            try {
                theDir.mkdir();
                result = true;
            } catch (SecurityException se) {
                //handle it
            }
            if (result) {
                System.out.println("DIR " + folder + " criado");
                mensagem("DIR " + folder + " criado");
                Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
            }
        }
    }

    /**
     * Efetua a verificação de erros do BD
     */
    public void gfixverif() {
        mensagem("Verificar integridade do BD");
        atualizaBarras();
        String command = System.getProperty("user.dir") + "\\gfix.exe";
//        String command = System.getProperty("user.dir") + "\\gfix.exe";
        String arquivoBanco = System.getProperty("user.dir") + "\\MGFP01.fdb ";
        String parametros = " -v -f ";
        String senha = "masterkey";
        String usuario = "SYSDBA";
        String login = " -USER " + usuario + " -PAS " + senha + " ";
        String comando = command + parametros + arquivoBanco + login;
        try {
            Process process = Runtime.getRuntime().exec(comando);
            try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String linha;
                while ((linha = input.readLine()) != null) {
                    mensagem("Executando: " + linha);
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, mensagem("Erro ao tentar verificar erros no BD! Erro: " + ex.getMessage()), "Erro!", JOptionPane.INFORMATION_MESSAGE);
        } finally {
            mensagem("Verificação do BD executada");
            Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
        }
    }

    /**
     * Efetua a correção de erros do BD
     */
    public void gfixCor() {
        mensagem("Verificar e corrigir integridade do BD");
        atualizaBarras();
        String command = System.getProperty("user.dir") + "\\gfix.exe";
        String arquivoBanco = System.getProperty("user.dir") + "\\MGFP01.fdb ";
        String parametros = " -m -i ";
        String senha = "masterkey";
        String usuario = "SYSDBA";
        String login = " -USER " + usuario + " -PAS " + senha + " ";
        String comando = command + parametros + arquivoBanco + login;
        try {
            Process process = Runtime.getRuntime().exec(comando);
            try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String linha;
                while ((linha = input.readLine()) != null) {
                    mensagem("Executando: " + linha);
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, mensagem("Erro ao tentar corrigir erros no BD! Erro: " + ex.getMessage()), "Erro!", JOptionPane.INFORMATION_MESSAGE);
        } finally {
            mensagem("Correção de erros do BD executada");
            Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
        }
    }

    /**
     * Efetua o Backup do BD
     *
     * @param c a = antes; d = depois
     */
    public void gbak(String c) {
        mensagem("Executar backup do BD");
        atualizaBarras();
        String command = System.getProperty("user.dir") + "\\gbak.exe";
        String arquivoBanco = System.getProperty("user.dir") + "\\MGFP01.fdb ";
        String arquivoBackup = System.getProperty("user.dir") + "\\MGFP01_" + c + ".fbk";
        String senha = "masterkey";
        String usuario = "SYSDBA";
        String login = " -USER " + usuario + " -PAS " + senha + " ";
        String parametros = " -g -b -z -l -v ";
        String comando = command + parametros + arquivoBanco + arquivoBackup + login;
        System.out.println(comando);
        try {
            Process process = Runtime.getRuntime().exec(comando);
            try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String linha;
                while ((linha = input.readLine()) != null) {
                    mensagem("Executando: " + linha);
                    atualizaBarras();
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, mensagem("Erro do backup do BD! Erro: " + ex.getMessage()), "Erro!", JOptionPane.INFORMATION_MESSAGE);
        } finally {
            mensagem("Gbak executado");
            Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
        }
    }

    /**
     * Efetua o Restore do BD
     *
     * @param c a = antes; d = depois
     */
    public void gbakres(String c) {
        mensagem("Executar restore do BD");
        atualizaBarras();
        String command = System.getProperty("user.dir") + "\\gbak.exe";
        String arquivoBanco = System.getProperty("user.dir") + "\\MGFP01.fdb ";
        String arquivoBackup = System.getProperty("user.dir") + "\\MGFP01_" + c + ".fbk ";
        String senha = "masterkey";
        String usuario = "SYSDBA";
        String login = " -USER " + usuario + " -PAS " + senha + " ";
        String parametros = " -rep -v -r -p 4096 -o ";
        String comando = command + parametros + arquivoBackup + arquivoBanco + login;
        System.out.println(comando);
        try {
            Process process = Runtime.getRuntime().exec(comando);
            try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String linha;
                while ((linha = input.readLine()) != null) {
                    mensagem("Executando: " + linha);
                    atualizaBarras();
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, mensagem("Erro do backup do BD! Erro: " + ex.getMessage()), "Erro!", JOptionPane.INFORMATION_MESSAGE);
        } finally {
            mensagem("Gres executado");
            Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
        }
    }

    public void deletebakd() {
        mensagem("Excluir backup do BD");
        atualizaBarras();
        try {
            File file = new File(System.getProperty("user.dir") + "\\MGFP01_d.fbk");
            file.delete();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, mensagem("Erro ao tentar excluir o backup do BD! Erro: " + ex.getMessage()), "Erro!", JOptionPane.INFORMATION_MESSAGE);
        } finally {
            mensagem("Exclusão do BD executada");
            Principal.getpBarAtual().setValue(Principal.getpBarAtual().getMaximum());
        }
    }

    public static File gravaArquivoDeURL(String stringUrl, String pathLocal, String nomeArquivoLocal) {
        try {
            //Encapsula a URL num objeto java.net.URL
            URL url = new URL(stringUrl);
            //Cria streams de leitura (este metodo ja faz a conexao)...
            InputStream is = url.openStream();
            //... e de escrita.
            FileOutputStream fos = new FileOutputStream(pathLocal + "\\" + nomeArquivoLocal);
            //Le e grava byte a byte. Voce pode (e deve) usar buffers para
            //melhor performance (BufferedReader).
            int umByte = 0;
            while ((umByte = is.read()) != -1) {
                fos.write(umByte);
            }
            //Nao se esqueca de sempre fechar as streams apos seu uso!
            is.close();
            fos.close();
            //apos criar o arquivo fisico, retorna referencia para o mesmo
            return new File(pathLocal + nomeArquivoLocal);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Erro: " + e.getMessage() + " -> " + e.getCause());
        }
        return null;
    }

}
