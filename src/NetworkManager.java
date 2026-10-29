import Dto.PeersDto;
import Validator.PeerAddressValidator;
import Validator.HelloValidator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;

public class NetworkManager {
  private final int port = 18018;
  private static final String PEER_FILE_PATH = "peers.json";
  private Set<String> knownPeers = loadPeers();
  private final Set<String> activeConnections = ConcurrentHashMap.newKeySet();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String selfAddress = "9.205.248.253:18018";
  public void start() {
    new Thread(this::connectToBootstrap).start();
    new Thread(this::discoverPeers).start();

    try (ServerSocket serverSocket = new ServerSocket(port)) {
      System.out.println("Server listens to port " + port);
      while (true) {
        Socket socket = serverSocket.accept();
        System.out.println("New peer: " + socket.getRemoteSocketAddress());

        new Thread(() -> handlePeerConnection(socket)).start();
      }
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
  }

  private void discoverPeers() {
    while (true) {
      try {
        int nodeConnections = 3;

        if (activeConnections.size() < nodeConnections) {
          List<String> peerList = new ArrayList<>(knownPeers);
          Collections.shuffle(peerList);
          peerList.remove(selfAddress);

          for (String peer : peerList) {
            if (activeConnections.size() >= nodeConnections) {
              break;
            }
            if (activeConnections.contains(peer)) {
              continue;
            }

            if (!PeerAddressValidator.isValid(peer)) {
              knownPeers.remove(peer);
              continue;
            }
            PeerAddressValidator.Address address = PeerAddressValidator.parse(peer);

            String host = address.host();
            int peerPort = address.port();

            activeConnections.add(peer);

            new Thread(() -> {
              try {
                System.out.println("Attempting to connect to: " + peer);
                Socket socket = new Socket(host, peerPort);
                handlePeerConnection(socket);
              } catch (IOException e) {
                System.out.println("Connection failed to " + peer + ", picking another peer");
                knownPeers.remove(peer);
              } finally {
                activeConnections.remove(peer);
              }
            }).start();
          }
        }
        Thread.sleep(30000);

      } catch (InterruptedException e){
        System.out.println("Peer discovery loop interrupted.");
        break;
      }
    }
  }

  private void connectToBootstrap() {
    try {
      String bootstrapHost = "128.130.122.101";
      Socket socket = new Socket(bootstrapHost, port);
      System.out.println("Successfully connected to bootstrap node");

      handlePeerConnection(socket);
    } catch (IOException e) {
      System.out.println("error connecting to bootstrap");
    }
  }

  private void handlePeerConnection(Socket socket) {
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

      if(!handleHelloMsg(socket, reader, writer)) {
        return;
      }

      String line;
      while (!socket.isClosed() && (line = reader.readLine()) != null) {
        if(line.isBlank()){
          sendError(writer, "INVALID_FORMAT", "Empty message");
          break;
        }
        processMessage(socket, writer, line);
      }

    } catch (IOException e) {
      System.out.println("Connection error with peer: " + e.getMessage());
    }
    finally{
      try { socket.close(); } catch (IOException ignored) {}
    }
  }

  private boolean handleHelloMsg(Socket socket, BufferedReader reader, PrintWriter writer) {
    try {
      String helloMsg = "{\"type\":\"hello\",\"version\":\"0.10.0\",\"agent\":\"Pitak\"}";
      writer.print(helloMsg + "\n");
      writer.flush();

      System.out.println("Sent the hello message");
      sendGetPeersMsg(writer);

      System.out.println("Sent the getpeers message");

      socket.setSoTimeout(20000);
      String firstLine = reader.readLine();

      if (firstLine == null) {
        sendError(writer, "INVALID_HANDSHAKE", "Either the connection was closed or the first line was not hello");
        socket.close();
        return false;
      }

      int result = HelloValidator.isValidHello(firstLine);
      if (result == -1) {
        throw new IOException("");
      }

      if (result == 0) {
        sendError(writer, "INVALID_FORMAT", "invalid format");
        try { socket.close(); } catch (IOException ignored) {}
        return false;
      }

      socket.setSoTimeout(0);
      System.out.println("Handshake successful with" + socket.getRemoteSocketAddress());
      return true;
    } catch (IOException e) {
      sendError(writer, "INVALID_HANDSHAKE", "IO error during handshake");
      try { socket.close(); } catch (IOException ignored) {}
      return false;
    }
  }

  private void sendGetPeersMsg(PrintWriter writer) {
    String getPeersMsg = "{\"type\":\"getpeers\"}";
    writer.print(getPeersMsg + "\n");
    writer.flush();
  }

  private void processMessage(Socket socket, PrintWriter writer, String line) {
    try {
      JsonNode json = objectMapper.readTree(line);

      if (json == null || !json.isObject()
          || !json.path("type").isTextual()) {
        sendError(writer, "INVALID_FORMAT", "Invalid JSON message");
        socket.close();
        return;
      }

      String type = json.get("type").asText();

      switch (type) {
        case "hello":
          sendError(writer, "INVALID_HANDSHAKE", "Unexpected second hello message");
          try { socket.close(); } catch (IOException ignored) {}
          break;

        case "getpeers":
          if (json.size() != 1) {
            sendError(writer, "INVALID_FORMAT", "Invalid getpeers message");
            socket.close();
            break;
          }
          sendPeersMsg(socket, writer, json);
          break;

        case "peers":
          handlePeersMsg(socket, writer, json);
          break;

        default:
          break;
      }
    } catch (IOException e) {
      sendError(writer, "INVALID_FORMAT", "IO parsing error occurred");
      try { socket.close(); } catch (IOException ignored) {}
    }
  }

  private void handlePeersMsg(Socket socket, PrintWriter writer, JsonNode json) {
    try {
        if (json.size() != 2 || !json.path("peers").isArray() || json.get("peers").size() > 30) {
          sendError(writer, "INVALID_FORMAT", "Invalid peers message");
          socket.close();
          return;
        }

        for (JsonNode peer : json.get("peers")) {
          if (!peer.isTextual() || !PeerAddressValidator.isValid(peer.asText())) {
            sendError(writer, "INVALID_FORMAT", "Invalid peer address");
            socket.close();
            return;
          }
        }

        PeersDto dto = objectMapper.treeToValue(json, PeersDto.class);

        knownPeers.addAll(dto.peers);
        savePeers(knownPeers);

        System.out.println("Saved " + dto.peers.size() + " peers.");
      } catch (IOException e) {
      System.out.println("Error processing message: " + e.getMessage());
      try { socket.close(); } catch (IOException ignored) {}
    }
  }

  private void sendPeersMsg(Socket socket, PrintWriter writer, JsonNode json) {
    try {
      if (json.size() != 1) {
        sendError(writer, "INVALID_FORMAT", "Invalid peers message");
        socket.close();
        return;
      }
    } catch (IOException ignored) {}
    String getPeersMsg;
    try {
      List<String> peers = knownPeers.stream().filter(peer -> !peer.equals(selfAddress)).limit(29).collect(Collectors.toList());

      peers.addFirst(selfAddress);
      PeersDto dto = new PeersDto();
      dto.peers = peers;

      getPeersMsg = objectMapper.writeValueAsString(dto);
    } catch (Exception e) {
      getPeersMsg = "{\"type\":\"peers\",\"peers\":[]}";
    }
    writer.print(getPeersMsg + "\n");
    writer.flush();
  }

  public static void savePeers(Set<String> peers) {
    try {
      Files.write(Path.of(PEER_FILE_PATH), peers);
    } catch (IOException e) {
      System.err.println("Could not save peers to disk: " + e.getMessage());
    }
  }

  public static Set<String> loadPeers() {
    Set<String> peers = ConcurrentHashMap.newKeySet();
    File file = new File(PEER_FILE_PATH);

    if (file.exists()) {
      try {
        java.util.List<String> lines = Files.readAllLines(Path.of(PEER_FILE_PATH));
        peers.addAll(lines);
        System.out.println("Loaded " + peers.size() + " peers from disk.");
      } catch (IOException e) {
        System.err.println("Could not load stored peers: " + e.getMessage());
      }
    }
    return peers;
  }

  private void sendError(PrintWriter writer, String name, String msg) {
    try {
      var json = objectMapper.createObjectNode();

      json.put("msg", msg);
      json.put("name", name);
      json.put("type", "error");

      writer.println(objectMapper.writeValueAsString(json));

    } catch (IOException e) {
      System.err.println("Could not send error: " + e.getMessage());
    }
  }
}
