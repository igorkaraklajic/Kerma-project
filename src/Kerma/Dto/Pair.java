package Kerma.Dto;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

public record Pair(BufferedReader in, PrintWriter out, Socket socket) {
}
