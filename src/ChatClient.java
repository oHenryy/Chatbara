import java.io.*;
import java.net.*;
import java.util.*;

public class ChatClient {
    private static BufferedReader in;
    private static PrintWriter out;
    private static boolean running = true;

    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 12345);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        new Thread(new IncomingReader()).start();

        Scanner scanner = new Scanner(System.in);
        while (running) {
            System.out.print("> ");
            String command = scanner.nextLine();

            if (isAdminCommand(command)) {
                System.out.println("Comando não permitido neste módulo: " + command);
                continue;
            }

            out.println(command);
            System.out.println("Comando enviado: " + command);

            if (command.startsWith("LOGOUT")) {
                running = false;
            }
        }

        socket.close();
        scanner.close();
    }

    private static boolean isAdminCommand(String command) {
        return command.startsWith("REGISTER") || command.startsWith("KILL");
    }

    private static class IncomingReader implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while (running && (message = in.readLine()) != null) {
                    if (message.equals("USERS_LIST")) {
                        System.out.println("Lista de usuários cadastrados:");
                        while (!(message = in.readLine()).equals("END_USERS_LIST")) {
                            System.out.println(message);
                        }
                    } else if (message.startsWith("HELP:")) {
                        System.out.println("Comandos disponíveis:");
                        while (!(message = in.readLine()).equals("")) {
                            System.out.println(message);
                        }
                    } else if (message.startsWith("KILLED")) {
                        System.out.println(message);
                        running = false;
                        break;
                    } else {
                        System.out.println(message);
                    }

                    if (message.startsWith("LOGOUT SUCCESS")) {
                        running = false;
                        break;
                    }
                }
            } catch (SocketException e) {
                System.out.println("Conexão fechada: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("Erro de conexão: " + e.getMessage());
                attemptReconnect();
            }
        }

        private void attemptReconnect() {
            while (running) {
                try {
                    System.out.println("Tentando reconectar...");
                    Socket socket = new Socket("localhost", 12345);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out = new PrintWriter(socket.getOutputStream(), true);
                    new Thread(new IncomingReader()).start();
                    break;
                } catch (IOException e) {
                    System.err.println("Falha na reconexão, tentando novamente em 5 segundos...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
