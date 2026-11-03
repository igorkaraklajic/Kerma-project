import Dto.PeersDto;
import Dto.TransactionDto;
import Utils.HashUtils;
import Validator.PeerAddressValidator;
import Validator.HelloValidator;
import Validator.TransactionValidator;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;

public class NetworkManager {
  private final int port = 18018;
  private static final String PEER_FILE_PATH = "peers.json";
  private static final String OBJECTS_FILE_PATH = "objects.json";
  private Set<String> knownPeers = loadPeers();
  private Map<String, TransactionDto> knownObjectsMap = loadObjects();
  private final Set<String> activeConnections = ConcurrentHashMap.newKeySet();
  private Set<PrintWriter> activeConnectionsWriters = ConcurrentHashMap.newKeySet();
  private static final ObjectMapper objectMapper = new ObjectMapper();
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
    PrintWriter writer = null;
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

      if(!handleHelloMsg(socket, reader, writer)) {
        return;
      }

      activeConnectionsWriters.add(writer);

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
      try {
        if (writer != null) {
          activeConnectionsWriters.remove(writer);
        }
        socket.close();
      } catch (IOException ignored) {}
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

        case "getobject":
          handleGetObjectMsg(socket, writer, json);
          break;
        case "ihaveobject":
          handleIhaveObjectMsg(socket, writer, json);
          break;
        case "object":
          handleObjectMsg(socket, writer, json);
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

        knownPeers.addAll(dto.peers); //TODO add checks so it never has more than 30 entries
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

  private void handleIhaveObjectMsg(Socket socket, PrintWriter writer, JsonNode json) {
    if (json.size() != 2 || !json.path("objectid").isTextual() || !json.get("objectid").asText().matches("[0-9a-f]{64}")) {
      sendError(writer, "INVALID_FORMAT", "Invalid ihaveobject message");
      try {
        socket.close();
      }
      catch (IOException ignored) {}
      return;
    }

    String objectId = json.get("objectid").asText();
    if (knownObjectsMap.containsKey(objectId)) {
      return;
    }

    var request = objectMapper.createObjectNode();
    request.put("type", "getobject");
    request.put("objectid", objectId);
    try {
      writer.println(objectMapper.writeValueAsString(request));
    } catch (IOException e) {
      System.err.println("Could not send getobject: " + e.getMessage());
    }
  }

  private void handleGetObjectMsg(Socket socket, PrintWriter writer, JsonNode json) {
    if (json.size() != 2 || !json.path("objectid").isTextual() || !json.get("objectid").asText().matches("[0-9a-f]{64}")) {
      sendError(writer, "INVALID_FORMAT", "Invalid getobject message");
      try {
        socket.close();
      }
      catch (IOException ignored) {}
      return;
    }

    String objectId = json.get("objectid").asText();
    TransactionDto object = knownObjectsMap.get(objectId);
    if (object == null) {
      sendError(writer, "UNKNOWN_OBJECT", "Requested object is not known");
      return;
    }

    var response = objectMapper.createObjectNode();
    response.put("type", "object");
    response.set("object", objectMapper.valueToTree(object));
    try {
      writer.println(objectMapper.writeValueAsString(response));
    } catch (IOException e) {
      System.err.println("Could not send object: " + e.getMessage());
    }
  }

  private void handleObjectMsg(Socket socket, PrintWriter writer, JsonNode json) {
    try {
      //this object can be casted, check the JacksonDeductionForTransactionTest to understand how this works, before being pasted to the validator
      TransactionDto transactionDto = objectMapper.treeToValue(json.get("object"), TransactionDto.class);
      if (transactionDto == null) {
        sendError(writer, "INVALID_FORMAT", "Invalid object message");
        try { socket.close(); } catch (IOException ignored) {}
        return;
      }

      //TODO: Add validations here
      int status = TransactionValidator.isValidTransaction(json.get("object"), knownObjectsMap);

      switch (status) {
        case 1:
          sendError(writer, "INVALID_FORMAT", "Invalid transaction format");
          try { socket.close(); } catch (IOException ignored) {}
          break;
        case 2:
          sendError(writer, "INVALID_TX_OUTPOINT", "Outpoint index not found");
          try { socket.close(); } catch (IOException ignored) {}
          break;
        case 3:
          sendError(writer, "INVALID_TX_CONSERVATION", "Sum of inputs is less than sum of outputs");
          try { socket.close(); } catch (IOException ignored) {}
          break;
        case 4:
          sendError(writer, "INVALID_TX_SIGNATURE", "Signature verification failed");
          try { socket.close(); } catch (IOException ignored) {}
          break;
        case -1:
          sendError(writer, "UNKNOWN_OBJECT", "Transaction object not found");
          break;
        case 0:
          byte[] payload = objectMapper.writeValueAsBytes(json.get("object"));
          String hash = HashUtils.computeBlake2sHash(payload);

          if (!knownObjectsMap.containsKey(hash)) {
            knownObjectsMap.put(hash, transactionDto); //TODO: Check if there is a limit of entries
            saveObjects(knownObjectsMap);
            broadcastObjectMsg(hash);
          }
          break;
        default:
          break;
      }
    } catch (IOException e) {
      sendError(writer, "INVALID_FORMAT", "IO parsing error occurred");
      try { socket.close(); } catch (IOException ignored) {}
    }
  }

  private void broadcastObjectMsg(String objectId) {
    var message = objectMapper.createObjectNode();
    message.put("type", "ihaveobject");
    message.put("objectid", objectId);

    try {
      String announcement = objectMapper.writeValueAsString(message);
      for (PrintWriter peerWriter : activeConnectionsWriters) {
        synchronized (peerWriter) {
          peerWriter.println(announcement);
        }
      }
    } catch (IOException e) {
      System.err.println("Could not broadcast object: " + e.getMessage());
    }
  }

  private static void savePeers(Set<String> peers) {
    try {
      Files.write(Path.of(PEER_FILE_PATH), peers);
    } catch (IOException e) {
      System.err.println("Could not save peers to disk: " + e.getMessage());
    }
  }

  //here we basically delete the file and write to it again with new entries, so it's safe regarding duplicates, same applies to savePeers
   private static void saveObjects(Map<String, TransactionDto> objects) {
    try {
      List<String> lines = new ArrayList<>();
      for (Map.Entry<String, TransactionDto> e : objects.entrySet()) {
        lines.add(objectMapper.writeValueAsString(Map.of("objectId", e.getKey(), "objectValue", e.getValue())));
      }
      Files.write(Path.of(OBJECTS_FILE_PATH), lines);
    } catch (IOException e) {
      System.err.println("Could not save objects to disk: " + e.getMessage());
    }
  }

  private static Set<String> loadPeers() {
    Set<String> peers = ConcurrentHashMap.newKeySet();
    File file = new File(PEER_FILE_PATH);

    if (file.exists()) {
      try {
        List<String> lines = Files.readAllLines(Path.of(PEER_FILE_PATH));
        peers.addAll(lines);
        System.out.println("Loaded " + peers.size() + " peers from disk.");
      } catch (IOException e) {
        System.err.println("Could not load stored peers: " + e.getMessage());
      }
    }
    return peers;
  }

  private static Map<String, TransactionDto> loadObjects() {
    Map<String, TransactionDto> objects = new ConcurrentHashMap<>();
    File file = new File(OBJECTS_FILE_PATH);

    if (file.exists()) {
      try {
        List<String> lines = Files.readAllLines(Path.of(OBJECTS_FILE_PATH));
        for (String line : lines) {
          if (line.isBlank()) {
            continue;
          }
          JsonNode jsonNode = objectMapper.readTree(line);
          objects.put(jsonNode.get("objectId").asText(), objectMapper.treeToValue(jsonNode.get("objectValue"), TransactionDto.class));
        }
        System.out.println("Loaded " + objects.size() + " objects from disk.");
      } catch (IOException e) {
        System.err.println("Could not load stored objects: " + e.getMessage());
      }
    }
    return objects;
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
