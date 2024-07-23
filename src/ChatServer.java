import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final String USER_DATA_FILE = "user_data.txt";
    private static final String OFFLINE_MESSAGES_FILE = "offline_messages.txt";

    private static Map<String, Socket> onlineUsers = new HashMap<>();
    private static Map<String, List<String>> offlineMessages = new HashMap<>();
    private static Map<String, String[]> userCredentials = new HashMap<>();
    private static Map<String, String> userAttributes = new HashMap<>();  // Novo mapa para atributos adicionais
    private static Set<String> serverLoggedInUsers = new HashSet<>();  // Usuários logados no console do servidor

    public static void main(String[] args) throws IOException {
        loadUserData();
        loadOfflineMessages();

        ServerSocket serverSocket = new ServerSocket(12345);
        System.out.println("Servidor iniciado...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                saveUserData();
                saveOfflineMessages();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        new Thread(new CommandHandler()).start();  // Thread para aceitar comandos do console

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new ClientHandler(clientSocket).start();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private String username;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                while (true) {
                    String command = in.readLine();
                    if (command == null) {
                        break;
                    }
                    System.out.println("Recebido comando: " + command);  // Log de depuração
                    if (command.startsWith("HELP")) {
                        handleHelp(out);
                    } else if (command.startsWith("LOGIN")) {
                        handleLogin(command, out);
                    } else {
                        if (this.username == null) {
                            out.println("AUTH FAIL: Você deve estar autenticado para executar comandos.");
                            continue;
                        }

                        if (command.startsWith("REGISTER")) {
                            handleRegister(command, out);
                        } else if (command.startsWith("MESSAGE")) {
                            handleMessage(command, out);
                        } else if (command.startsWith("LOGOUT")) {
                            handleLogout(out);
                            break;
                        } else if (command.startsWith("LIST_USERS")) {
                            handleListUsers(out);
                        } else if (command.startsWith("KILL")) {
                            handleKill(command, out);
                        } else {
                            handleInvalidCommand(out);
                        }
                    }
                }
            } catch (SocketException e) {
                System.out.println("Socket fechado: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (username != null) {
                    onlineUsers.remove(username);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleRegister(String command, PrintWriter out) {
            if (!userCredentials.get(this.username)[1].equals("Tecnico")) {
                out.println("REGISTER FAIL: Apenas técnicos podem registrar novos usuários.");
                return;
            }

            String[] tokens = command.split(" ");
            if (tokens.length < 4) {
                out.println("REGISTER FAIL: Formato inválido. Use: REGISTER <username> <password> <tipo> [<titulação>/ <ano de ingresso>]");
                return;
            }
            String username = tokens[1];
            String password = tokens[2];
            String userType = tokens[3];

            if (userCredentials.containsKey(username)) {
                out.println("REGISTER FAIL: Usuário já registrado.");
                return;
            }

            String attribute = null;
            switch (userType) {
                case "Professor":
                    if (tokens.length < 5) {
                        out.println("REGISTER FAIL: Professores devem fornecer a titulação.");
                        return;
                    }
                    attribute = tokens[4];
                    break;
                case "Aluno":
                    if (tokens.length < 5) {
                        out.println("REGISTER FAIL: Alunos devem fornecer o ano de ingresso.");
                        return;
                    }
                    attribute = tokens[4];
                    break;
                case "Tecnico":
                    // Técnico não tem atributo adicional
                    break;
                default:
                    out.println("REGISTER FAIL: Tipo de usuário inválido. Use: Tecnico, Professor ou Aluno.");
                    return;
            }

            userCredentials.put(username, new String[]{password, userType});
            userAttributes.put(username, attribute);
            offlineMessages.put(username, new ArrayList<>());
            out.println("REGISTER SUCCESS");

            try {
                saveUserData();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleLogin(String command, PrintWriter out) {
            String[] tokens = command.split(" ");
            if (tokens.length < 3) {
                out.println("LOGIN FAIL: Formato inválido. Use: LOGIN <username> <password>");
                return;
            }
            String username = tokens[1];
            String password = tokens[2];
            if (userCredentials.containsKey(username) && password.equals(userCredentials.get(username)[0])) {
                this.username = username;
                onlineUsers.put(username, socket);
                out.println("LOGIN SUCCESS");
                sendOfflineMessages(out);
            } else {
                out.println("LOGIN FAIL: Usuário ou senha inválidos.");
            }
        }

        private void handleMessage(String command, PrintWriter out) {
            String[] tokens = command.split(" ", 3);
            if (tokens.length < 3) {
                out.println("MESSAGE FAIL: Formato inválido. Use: MESSAGE <recipient> <message>");
                return;
            }
            String recipient = tokens[1];
            String message = tokens[2];
            System.out.println("Processando mensagem de " + username + " para " + recipient + ": " + message);

            if (onlineUsers.containsKey(recipient)) {
                try {
                    Socket recipientSocket = onlineUsers.get(recipient);
                    PrintWriter recipientOut = new PrintWriter(recipientSocket.getOutputStream(), true);
                    recipientOut.println("MESSAGE " + username + ": " + message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                List<String> messages = offlineMessages.computeIfAbsent(recipient, k -> new ArrayList<>());
                messages.add("MESSAGE " + username + ": " + message);
                try {
                    saveOfflineMessages();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void sendOfflineMessages(PrintWriter out) {
            List<String> messages = offlineMessages.get(username);
            if (messages != null) {
                for (String message : messages) {
                    out.println(message);
                }
                messages.clear();
                try {
                    saveOfflineMessages();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleLogout(PrintWriter out) {
            if (username != null) {
                onlineUsers.remove(username);
                out.println("LOGOUT SUCCESS");
            }
        }

        private void handleListUsers(PrintWriter out) {
            out.println("USERS_LIST");
            for (String user : userCredentials.keySet()) {
                String status = onlineUsers.containsKey(user) ? "Online" : serverLoggedInUsers.contains(user) ? "Online no Servidor" : "Offline";
                String userType = userCredentials.get(user)[1];
                String attribute = userAttributes.get(user);
                String attributeLabel = userType.equals("Professor") ? "Titulação" : (userType.equals("Aluno") ? "Ano de ingresso" : "");
                out.println(user + " (" + userType + ") - " + (attributeLabel.isEmpty() ? "" : attributeLabel + ": " + attribute + " - ") + status);
            }
            out.println("END_USERS_LIST");
        }

        private void handleHelp(PrintWriter out) {
            out.println("HELP:");
            out.println("REGISTER <username> <password> <tipo> [<titulação>/ <ano de ingresso>]");
            out.println("LOGIN <username> <password>");
            out.println("MESSAGE <recipient> <message>");
            out.println("LOGOUT");
            out.println("LIST_USERS");
            out.println("KILL <username>/ALL");
            out.println("HELP");
        }

        private void handleInvalidCommand(PrintWriter out) {
            out.println("Comando inválido. Digite HELP para ver a lista de comandos disponíveis.");
        }

        private void handleKill(String command, PrintWriter out) {
            if (!userCredentials.get(username)[1].equals("Tecnico")) {
                out.println("KILL FAIL: Apenas técnicos podem usar este comando.");
                return;
            }

            String[] tokens = command.split(" ");
            if (tokens.length < 2) {
                out.println("KILL FAIL: Formato inválido. Use: KILL <username>/ALL");
                return;
            }

            String target = tokens[1];
            if (target.equals("ALL")) {
                for (String user : new HashSet<>(onlineUsers.keySet())) {
                    disconnectUser(user);
                }
                out.println("KILL SUCCESS: Todos os usuários foram desconectados.");
            } else {
                if (onlineUsers.containsKey(target)) {
                    disconnectUser(target);
                    out.println("KILL SUCCESS: Usuário " + target + " foi desconectado.");
                } else {
                    out.println("KILL FAIL: Usuário " + target + " não está online.");
                }
            }
        }

        private void disconnectUser(String user) {
            try {
                Socket userSocket = onlineUsers.remove(user);
                if (userSocket != null) {
                    PrintWriter userOut = new PrintWriter(userSocket.getOutputStream(), true);
                    userOut.println("KILLED: Você foi desconectado pelo técnico.");
                    userSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class CommandHandler implements Runnable {
        private boolean isAuthenticated = false;
        private String authenticatedUser = null;

        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            PrintWriter out = new PrintWriter(System.out, true);

            while (true) {
                if (!isAuthenticated) {
                    System.out.print("LOGIN <username> <password>: ");
                    String command = scanner.nextLine();
                    String[] tokens = command.split(" ");
                    if (tokens.length == 3 && tokens[0].equals("LOGIN")) {
                        String username = tokens[1];
                        String password = tokens[2];
                        if (userCredentials.containsKey(username) && password.equals(userCredentials.get(username)[0]) && userCredentials.get(username)[1].equals("Tecnico")) {
                            isAuthenticated = true;
                            authenticatedUser = username;
                            serverLoggedInUsers.add(username);  // Marcar técnico como logado no console
                            out.println("LOGIN SUCCESS");
                        } else {
                            out.println("LOGIN FAIL: Usuário ou senha inválidos, ou você não tem permissões de técnico.");
                        }
                    } else {
                        out.println("LOGIN FAIL: Formato inválido. Use: LOGIN <username> <password>");
                    }
                } else {
                    System.out.print(authenticatedUser + "> ");
                    String command = scanner.nextLine();
                    System.out.println("Comando recebido no console do servidor: " + command);

                    // Processar o comando como se fosse de um cliente fictício "admin"
                    processServerCommand(command, out);
                }
            }
        }

        private void processServerCommand(String command, PrintWriter out) {
            if (command.startsWith("HELP")) {
                handleHelp(out);
            } else if (command.startsWith("REGISTER")) {
                handleRegister(command, out);
            } else if (command.startsWith("MESSAGE")) {
                out.println("MESSAGE FAIL: Comando não permitido no console do servidor.");
            } else if (command.startsWith("LOGOUT")) {
                handleLogout(out);
            } else if (command.startsWith("LIST_USERS")) {
                handleListUsers(out);
            } else if (command.startsWith("KILL")) {
                handleKill(command, out);
            } else {
                handleInvalidCommand(out);
            }
        }

        private void handleHelp(PrintWriter out) {
            out.println("HELP:");
            out.println("REGISTER <username> <password> <tipo> [<titulação>/ <ano de ingresso>]");
            out.println("MESSAGE <recipient> <message>");
            out.println("LIST_USERS");
            out.println("KILL <username>/ALL");
            out.println("LOGOUT");
            out.println("HELP");
        }

        private void handleRegister(String command, PrintWriter out) {
            String username = authenticatedUser; // Usuário autenticado no console do servidor

            String[] tokens = command.split(" ");
            if (tokens.length < 4) {
                out.println("REGISTER FAIL: Formato inválido. Use: REGISTER <username> <password> <tipo> [<titulação>/ <ano de ingresso>]");
                return;
            }
            username = tokens[1];
            String password = tokens[2];
            String userType = tokens[3];

            if (userCredentials.containsKey(username)) {
                out.println("REGISTER FAIL: Usuário já registrado.");
                return;
            }

            String attribute = null;
            switch (userType) {
                case "Professor":
                    if (tokens.length < 5) {
                        out.println("REGISTER FAIL: Professores devem fornecer a titulação.");
                        return;
                    }
                    attribute = tokens[4];
                    break;
                case "Aluno":
                    if (tokens.length < 5) {
                        out.println("REGISTER FAIL: Alunos devem fornecer o ano de ingresso.");
                        return;
                    }
                    attribute = tokens[4];
                    break;
                case "Tecnico":
                    // Técnico não tem atributo adicional
                    break;
                default:
                    out.println("REGISTER FAIL: Tipo de usuário inválido. Use: Tecnico, Professor ou Aluno.");
                    return;
            }

            userCredentials.put(username, new String[]{password, userType});
            userAttributes.put(username, attribute);
            offlineMessages.put(username, new ArrayList<>());
            out.println("REGISTER SUCCESS");

            try {
                saveUserData();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleKill(String command, PrintWriter out) {
            String username = authenticatedUser;  // Usuário autenticado no console do servidor

            String[] tokens = command.split(" ");
            if (tokens.length < 2) {
                out.println("KILL FAIL: Formato inválido. Use: KILL <username>/ALL");
                return;
            }

            String target = tokens[1];
            if (target.equals("ALL")) {
                for (String user : new HashSet<>(onlineUsers.keySet())) {
                    disconnectUser(user, out);
                }
                out.println("KILL SUCCESS: Todos os usuários foram desconectados.");
            } else {
                if (onlineUsers.containsKey(target)) {
                    disconnectUser(target, out);
                    out.println("KILL SUCCESS: Usuário " + target + " foi desconectado.");
                } else {
                    out.println("KILL FAIL: Usuário " + target + " não está online.");
                }
            }
        }

        private void disconnectUser(String user, PrintWriter out) {
            try {
                Socket userSocket = onlineUsers.remove(user);
                if (userSocket != null) {
                    PrintWriter userOut = new PrintWriter(userSocket.getOutputStream(), true);
                    userOut.println("KILLED: Você foi desconectado pelo técnico.");
                    userSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleListUsers(PrintWriter out) {
            out.println("USERS_LIST");
            for (String user : userCredentials.keySet()) {
                String status = onlineUsers.containsKey(user) ? "Online" : serverLoggedInUsers.contains(user) ? "Online no Servidor" : "Offline";
                String userType = userCredentials.get(user)[1];
                String attribute = userAttributes.get(user);
                String attributeLabel = userType.equals("Professor") ? "Titulação" : (userType.equals("Aluno") ? "Ano de ingresso" : "");
                out.println(user + " (" + userType + ") - " + (attributeLabel.isEmpty() ? "" : attributeLabel + ": " + attribute + " - ") + status);
            }
            out.println("END_USERS_LIST");
        }

        private void handleLogout(PrintWriter out) {
            if (isAuthenticated) {
                serverLoggedInUsers.remove(authenticatedUser);  // Remover técnico da lista de logados no console
                isAuthenticated = false;
                authenticatedUser = null;
                out.println("LOGOUT SUCCESS");
            }
        }

        private void handleInvalidCommand(PrintWriter out) {
            out.println("Comando inválido. Digite HELP para ver a lista de comandos disponíveis.");
        }
    }

    private static void loadUserData() throws IOException {
        File file = new File(USER_DATA_FILE);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(" ");
                if (tokens.length < 3) {
                    System.err.println("Dados inválidos no arquivo: " + line);  // Log de erro
                    continue;
                }
                String username = tokens[0];
                String password = tokens[1];
                String userType = tokens[2];
                String attribute = tokens.length > 3 ? tokens[3] : null;
                userCredentials.put(username, new String[]{password, userType});
                userAttributes.put(username, attribute);
            }
        }
    }

    private static void saveUserData() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(USER_DATA_FILE))) {
            for (Map.Entry<String, String[]> entry : userCredentials.entrySet()) {
                String username = entry.getKey();
                String password = entry.getValue()[0];
                String userType = entry.getValue()[1];
                String attribute = userAttributes.get(username);
                writer.println(username + " " + password + " " + userType + (attribute != null ? " " + attribute : ""));
            }
        }
    }

    private static void loadOfflineMessages() throws IOException {
        File file = new File(OFFLINE_MESSAGES_FILE);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(" ", 2);
                if (tokens.length < 2) {
                    System.err.println("Dados inválidos no arquivo: " + line);  // Log de erro
                    continue;
                }
                String username = tokens[0];
                String message = tokens[1];
                offlineMessages.computeIfAbsent(username, k -> new ArrayList<>()).add(message);
            }
        }
    }

    private static void saveOfflineMessages() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(OFFLINE_MESSAGES_FILE))) {
            for (Map.Entry<String, List<String>> entry : offlineMessages.entrySet()) {
                String username = entry.getKey();
                for (String message : entry.getValue()) {
                    writer.println(username + " " + message);
                }
            }
        }
    }
}
