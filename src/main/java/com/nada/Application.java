package com.nada;

import com.nada.handler.PeopleHandler;
import com.nada.service.PeopleService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Application {

    /** Creates (but does not start) a server. Port 0 picks a free ephemeral port. */
    public static HttpServer createServer(int port, PeopleService service) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new PeopleHandler(service));
        server.setExecutor(Executors.newFixedThreadPool(8));
        return server;
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = createServer(port, new PeopleService());
        server.start();
        System.out.println("Nada People API running on port " + server.getAddress().getPort());
    }
}
