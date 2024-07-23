# Chatbara - Chat Application

Este projeto é uma aplicação de chat simples implementada em Java, composta por um cliente e um servidor. O servidor gerencia autenticação, registro de usuários e mensagens offline, enquanto o cliente permite a comunicação com o servidor.

## Estrutura do Projeto

- **ChatClient**: Implementa o cliente do chat.
- **ChatServer**: Implementa o servidor do chat.

## ChatClient

O `ChatClient` se conecta ao servidor, envia comandos e recebe mensagens. Os comandos disponíveis são:

- `LOGIN <username> <password>`: Autentica o usuário.
- `MESSAGE <recipient> <message>`: Envia uma mensagem para outro usuário.
- `LOGOUT`: Desconecta o usuário.
- `HELP`: Mostra a lista de comandos disponíveis.

### Restrições:
- Comandos administrativos (`REGISTER`, `KILL`, `LIST_USERS`) não podem ser executados pelo cliente.

## ChatServer

O `ChatServer` gerencia a autenticação e comunicação entre usuários. Ele possui as seguintes funcionalidades:

- **Registro de Usuários**: Salva e carrega dados de usuários de um arquivo (`user_data.txt`).
- **Mensagens Offline**: Armazena mensagens para usuários offline em um arquivo (`offline_messages.txt`).
- **Conexão e Desconexão**: Gerencia a conexão dos usuários e o envio de mensagens.

### Comandos do Servidor

- `REGISTER <username> <password> <tipo> [<titulação>/ <ano de ingresso>]`: Registra um novo usuário. Apenas técnicos podem registrar novos usuários.
- `LOGIN <username> <password>`: Autentica um usuário.
- `MESSAGE <recipient> <message>`: Envia uma mensagem para um usuário.
- `LOGOUT`: Desconecta um usuário.
- `LIST_USERS`: Lista todos os usuários. Técnicos logados no console do servidor são exibidos como "Online no Servidor".
- `KILL <username>/ALL`: Desconecta um usuário específico ou todos os usuários. Apenas técnicos podem usar este comando.
- `HELP`: Mostra a lista de comandos disponíveis.

### Comandos Administrativos no Console do Servidor

Além dos comandos acima, técnicos podem executar comandos administrativos diretamente no console do servidor:

1. **Login do Técnico no Console**:

    ```
    LOGIN <username> <password>
    ```

2. **Comandos Administrativos** (após login):

   - `REGISTER <username> <password> <tipo> [<titulação>/ <ano de ingresso>]`
   - `LIST_USERS`
   - `KILL <username>/ALL`
   - `LOGOUT`
   - `HELP`

## Configuração e Execução

1. **Compilar o Código**:
   
    ```
   javac ChatClient.java
   javac ChatServer.java
    ```

2. **Executar o servidor:**

    ```
   java ChatServer
    ```
   
3. **Executar o cliente:**

    ```
   java ChatClient
    ```

## Arquivos de Dados

- `user_data.txt`: Contém dados dos usuários registrados.
- `offline_messages.txt`: Armazena mensagens enviadas para usuários offline.

## Observações

- O servidor e o cliente devem estar na mesma máquina ou rede local, com o servidor escutando na porta 12345.
- Apenas usuários com o tipo "Técnico" podem registrar novos usuários e usar o comando `KILL`.

## Autores

- @oHenryy
- @RaffaellaSantos