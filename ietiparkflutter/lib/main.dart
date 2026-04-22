import 'package:flutter/material.dart';
import 'widgets/game_widget.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Juego Cooperativo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'Juego Cooperativo'),
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
  bool _mostrarJuego = true;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
        actions: [
          IconButton(
            icon: Icon(_mostrarJuego ? Icons.visibility_off : Icons.visibility),
            onPressed: () {
              setState(() {
                _mostrarJuego = !_mostrarJuego;
              });
            },
          ),
        ],
      ),
      body: _mostrarJuego
          ? const GameWidget()
          : Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.gamepad, size: 80, color: Colors.grey),
                  const SizedBox(height: 20),
                  const Text(
                    'Vista oculta\nPresiona el botón de "ver" para mostrar el juego',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 16),
                  ),
                  const SizedBox(height: 20),
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        _mostrarJuego = true;
                      });
                    },
                    child: const Text('Mostrar Juego'),
                  ),
                ],
              ),
            ),
    );
  }
}