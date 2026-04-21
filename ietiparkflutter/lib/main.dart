import 'package:flutter/material.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter WebSocket Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      home: const MyHomePage(title: 'Juego Online'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  late WebSocketChannel channel;
  String mensaje = "Esperando que inicie partida";

  @override
  void initState() {
    super.initState();

    // Conexión al WebSocket
    channel = WebSocketChannel.connect(Uri.parse('wss://pico2.ieti.site'));

    // Escuchar mensajes del servidor
    channel.stream.listen(
      (data) {
        setState(() {
          mensaje = data.toString();
        });
      },
      onError: (error) {
        setState(() {
          mensaje = "Error de conexión";
        });
      },
      onDone: () {
        setState(() {
          mensaje = "Conexión cerrada";
        });
      },
    );
  }

  @override
  void dispose() {
    channel.sink.close();
    super.dispose();
  }

  void _enviarMensaje() {
    channel.sink.add("Hola servidor");
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              mensaje,
              style: Theme.of(context).textTheme.headlineMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: _enviarMensaje,
              child: const Text("Enviar mensaje"),
            ),
          ],
        ),
      ),
    );
  }
}
