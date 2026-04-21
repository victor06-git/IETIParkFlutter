import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import '../models/game_models.dart';

class GameWidget extends StatefulWidget {
  const GameWidget({super.key});

  @override
  State<GameWidget> createState() => _GameWidgetState();
}

class _GameWidgetState extends State<GameWidget> {
  late Future<LevelData> _levelData;
  Offset _cameraPosition = Offset.zero;
  double _zoom = 1.0;

  // Variables para animación de demostración
  double _demoPlayerX = 400;
  double _demoPlayerY = 350;
  int _demoFrame = 0;
  Timer? _animationTimer;
  double _direction = 1; // 1 derecha, -1 izquierda
  String _currentAnimation = 'idle';
  int _animationFrame = 0;

  @override
  void initState() {
    super.initState();
    _levelData = LevelData.loadFromAssets();
    _cameraPosition = const Offset(0, 0);
    
    // Animación de demostración
    _animationTimer = Timer.periodic(const Duration(milliseconds: 50), (timer) {
      setState(() {
        _demoFrame++;
        _animationFrame = (_animationFrame + 1) % 7;
        
        // Movimiento del personaje
        _demoPlayerX += _direction * 5;
        
        // Rebote en los bordes
        if (_demoPlayerX > 1200) {
          _direction = -1;
          _demoPlayerX = 1200;
          _currentAnimation = 'idle';
        } else if (_demoPlayerX < 400) {
          _direction = 1;
          _demoPlayerX = 400;
          _currentAnimation = 'idle';
        } else {
          _currentAnimation = 'run';
        }
        
        // Simular gravedad en rampas
        bool enRampa = false;
        if (_demoPlayerX > 850 && _demoPlayerX < 950) {
          _demoPlayerY = 340 + (_demoPlayerX - 850) * 0.3;
          enRampa = true;
        } else if (_demoPlayerX > 800 && _demoPlayerX < 880) {
          _demoPlayerY = 360;
        } else {
          _demoPlayerY = 370;
        }
        
        if (enRampa) {
          _currentAnimation = 'jump';
        }
        
        // Actualizar cámara
        _cameraPosition = Offset(_demoPlayerX - 400, _demoPlayerY - 200);
      });
    });
  }

  @override
  void dispose() {
    _animationTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<LevelData>(
      future: _levelData,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 20),
                Text('Cargando nivel...'),
              ],
            ),
          );
        }

        if (snapshot.hasError) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error, size: 50, color: Colors.red),
                const SizedBox(height: 20),
                Text('Error: ${snapshot.error}'),
                const SizedBox(height: 20),
                ElevatedButton(
                  onPressed: () {
                    setState(() {
                      _levelData = LevelData.loadFromAssets();
                    });
                  },
                  child: const Text('Reintentar'),
                ),
              ],
            ),
          );
        }

        final levelData = snapshot.data!;
        
        return InteractiveViewer(
          minScale: 0.5,
          maxScale: 2.0,
          constrained: false,
          onInteractionEnd: (details) {},
          child: SizedBox(
            width: 2000,
            height: 600,
            child: CustomPaint(
              painter: GamePainter(
                zones: levelData.zones,
                layers: levelData.layers,
                animations: levelData.animations,
                playerX: _demoPlayerX,
                playerY: _demoPlayerY,
                playerFrame: _demoFrame,
                animationFrame: _animationFrame,
                currentAnimation: _currentAnimation,
                playerDirection: _direction,
                cameraOffset: _cameraPosition,
              ),
              size: const Size(2000, 600),
            ),
          ),
        );
      },
    );
  }
}

class GamePainter extends CustomPainter {
  final List<Zone> zones;
  final List<TileMap> layers;
  final Map<String, AnimationData> animations;
  final double playerX;
  final double playerY;
  final int playerFrame;
  final int animationFrame;
  final String currentAnimation;
  final double playerDirection;
  final Offset cameraOffset;

  GamePainter({
    required this.zones,
    required this.layers,
    required this.animations,
    required this.playerX,
    required this.playerY,
    required this.playerFrame,
    required this.animationFrame,
    required this.currentAnimation,
    required this.playerDirection,
    required this.cameraOffset,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // Aplicar offset de cámara
    canvas.save();
    canvas.translate(-cameraOffset.dx, -cameraOffset.dy);

    // Dibujar fondo
    final backgroundPaint = Paint()..color = Colors.grey[200]!;
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), backgroundPaint);

    // Dibujar cuadrícula de referencia
    _drawGrid(canvas, size);

    // Dibujar tiles de las capas
    for (var layer in layers) {
      _drawTileLayer(canvas, layer);
    }

    // Dibujar zonas
    for (var zone in zones) {
      _drawZone(canvas, zone);
    }

    // Dibujar personaje
    _drawPlayer(canvas);

    canvas.restore();
    
    // Dibujar UI
    _drawUI(canvas, size);
  }

  void _drawGrid(Canvas canvas, Size size) {
    final gridPaint = Paint()
      ..color = Colors.grey[300]!
      ..strokeWidth = 0.5;
    
    // Líneas verticales cada 50px
    for (double x = 0; x < size.width; x += 50) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), gridPaint);
    }
    
    // Líneas horizontales cada 50px
    for (double y = 0; y < size.height; y += 50) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), gridPaint);
    }
  }

  void _drawTileLayer(Canvas canvas, TileMap layer) {
    const tileSize = 32.0;
    
    final colors = [
      Colors.green[100]!,
      Colors.green[200]!,
      Colors.brown[100]!,
      Colors.brown[200]!,
      Colors.orange[100]!,
    ];
    
    final layerColor = colors[layer.layerIndex % colors.length].withOpacity(0.7);
    final tilePaint = Paint()..color = layerColor;
    
    for (int y = 0; y < layer.tiles.length; y++) {
      for (int x = 0; x < layer.tiles[y].length; x++) {
        final tileId = layer.tiles[y][x];
        if (tileId > 0) {
          canvas.drawRect(
            Rect.fromLTWH(x * tileSize, y * tileSize, tileSize, tileSize),
            tilePaint,
          );
          
          // Borde
          final borderPaint = Paint()
            ..color = Colors.black.withOpacity(0.1)
            ..style = PaintingStyle.stroke
            ..strokeWidth = 0.5;
          
          canvas.drawRect(
            Rect.fromLTWH(x * tileSize, y * tileSize, tileSize, tileSize),
            borderPaint,
          );
        }
      }
    }
  }

  void _drawZone(Canvas canvas, Zone zone) {
    final paint = Paint()
      ..color = zone.color
      ..style = PaintingStyle.fill;
    
    canvas.drawRect(zone.rect, paint);
    
    final borderPaint = Paint()
      ..color = Colors.black.withOpacity(0.3)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    
    canvas.drawRect(zone.rect, borderPaint);
    
    // Texto de zona
    final textPainter = TextPainter(
      text: TextSpan(
        text: zone.name,
        style: const TextStyle(
          color: Colors.black,
          fontSize: 10,
          fontWeight: FontWeight.bold,
        ),
      ),
      textDirection: TextDirection.ltr,
    );
    
    textPainter.layout();
    textPainter.paint(
      canvas,
      Offset(zone.x + 5, zone.y + 5),
    );
  }

  void _drawPlayer(Canvas canvas) {
    // Cuerpo
    final bodyPaint = Paint()
      ..color = Colors.orange
      ..style = PaintingStyle.fill;
    
    canvas.drawRect(
      Rect.fromLTWH(playerX - 20, playerY - 40, 40, 60),
      bodyPaint,
    );
    
    // Cabeza
    canvas.drawCircle(
      Offset(playerX, playerY - 45),
      18,
      bodyPaint,
    );
    
    // Orejas
    final earPaint = Paint()..color = Colors.orange[800]!;
    canvas.drawRect(
      Rect.fromLTWH(playerX - 25, playerY - 58, 12, 15),
      earPaint,
    );
    canvas.drawRect(
      Rect.fromLTWH(playerX + 13, playerY - 58, 12, 15),
      earPaint,
    );
    
    // Ojos
    final eyePaint = Paint()..color = Colors.white;
    canvas.drawCircle(
      Offset(playerX - (playerDirection > 0 ? 8 : -8), playerY - 50),
      5,
      eyePaint,
    );
    
    final pupilPaint = Paint()..color = Colors.black;
    canvas.drawCircle(
      Offset(playerX - (playerDirection > 0 ? 7 : -7), playerY - 50),
      3,
      pupilPaint,
    );
    
    // Nariz
    final nosePaint = Paint()..color = Colors.pink;
    canvas.drawCircle(
      Offset(playerX, playerY - 43),
      3,
      nosePaint,
    );
    
    // Bigotes
    final whiskerPaint = Paint()
      ..color = Colors.black
      ..strokeWidth = 1;
    
    canvas.drawLine(
      Offset(playerX - 15, playerY - 45),
      Offset(playerX - 25, playerY - 48),
      whiskerPaint,
    );
    canvas.drawLine(
      Offset(playerX - 15, playerY - 43),
      Offset(playerX - 25, playerY - 43),
      whiskerPaint,
    );
    canvas.drawLine(
      Offset(playerX + 15, playerY - 45),
      Offset(playerX + 25, playerY - 48),
      whiskerPaint,
    );
    canvas.drawLine(
      Offset(playerX + 15, playerY - 43),
      Offset(playerX + 25, playerY - 43),
      whiskerPaint,
    );
    
    // Cola
    final tailPaint = Paint()
      ..color = Colors.orange
      ..strokeWidth = 4
      ..strokeCap = StrokeCap.round;
    
    final tailAngle = sin(playerFrame * 0.3) * 0.5;
    canvas.drawLine(
      Offset(playerX + (playerDirection > 0 ? 20 : -20), playerY - 20),
      Offset(
        playerX + (playerDirection > 0 ? 35 : -35) + cos(tailAngle) * 10,
        playerY - 30 + sin(tailAngle) * 10,
      ),
      tailPaint,
    );
    
    // Animación de salto (líneas de movimiento)
    if (currentAnimation == 'jump') {
      final jumpPaint = Paint()
        ..color = Colors.white.withOpacity(0.7)
        ..strokeWidth = 2;
      
      for (int i = 0; i < 3; i++) {
        canvas.drawLine(
          Offset(playerX - 30 + i * 10, playerY + 10),
          Offset(playerX - 40 + i * 10, playerY + 20),
          jumpPaint,
        );
      }
    }
    
    // Hitbox (debug)
    final debugPaint = Paint()
      ..color = Colors.red
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    
    canvas.drawRect(
      Rect.fromLTWH(playerX - 20, playerY - 40, 40, 60),
      debugPaint,
    );
    
    // Nombre de animación actual
    final animText = TextPainter(
      text: TextSpan(
        text: currentAnimation,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 10,
          fontWeight: FontWeight.bold,
          backgroundColor: Colors.black54,
        ),
      ),
      textDirection: TextDirection.ltr,
    );
    
    animText.layout();
    animText.paint(
      canvas,
      Offset(playerX - 15, playerY - 65),
    );
  }

  void _drawUI(Canvas canvas, Size size) {
    final animationsList = animations.values.toList();
    final animationsInfo = animationsList.take(5).map((a) => a.name).join(', ');
    
    final textPainter = TextPainter(
      text: TextSpan(
        text: '=== MODO DEMO ===\n'
               '🎮 Personaje en movimiento automático\n'
               '📊 Zonas cargadas: ${zones.length}\n'
               '🗺️ Capas cargadas: ${layers.length}\n'
               '🎬 Animaciones: ${animations.length}\n'
               '📍 Posición: (${playerX.toStringAsFixed(0)}, ${playerY.toStringAsFixed(0)})\n'
               '🎭 Animación actual: $currentAnimation\n'
               '➡️ Dirección: ${playerDirection > 0 ? "Derecha" : "Izquierda"}\n'
               '🖼️ Frame: $animationFrame/7\n'
               '\n✨ Próximamente: Conexión WebSocket con Android',
        style: const TextStyle(
          color: Colors.white,
          fontSize: 12,
          fontWeight: FontWeight.bold,
          backgroundColor: Colors.black54,
        ),
      ),
      textDirection: TextDirection.ltr,
    );
    
    textPainter.layout();
    textPainter.paint(canvas, const Offset(10, 10));
    
    // Leyenda de colores
    double yOffset = size.height - 100;
    final legendPaint = Paint()
      ..color = Colors.black54
      ..style = PaintingStyle.fill;
    
    canvas.drawRect(
      Rect.fromLTWH(10, yOffset - 5, 200, 95),
      legendPaint,
    );
    
    final legendText = TextPainter(
      text: const TextSpan(
        text: 'Leyenda:\n'
               '🔴 Rojo = Muros\n'
               '🔵 Azul = Suelo\n'
               '🟡 Amarillo = Rampas\n'
               '🟢 Verde = Zonas especiales',
        style: TextStyle(
          color: Colors.white,
          fontSize: 10,
        ),
      ),
      textDirection: TextDirection.ltr,
    );
    
    legendText.layout();
    legendText.paint(canvas, Offset(15, size.height - 95));
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) {
    return true;
  }
}