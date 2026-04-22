import 'dart:async';
import 'dart:ui' as ui;
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

/// Carga y cachea imágenes de assets como dart:ui.Image para renderizar en Canvas.
class SpriteLoader {
  final Map<String, ui.Image> _cache = {};
  final Map<String, Completer<ui.Image?>> _loading = {};

  bool isLoaded(String assetPath) => _cache.containsKey(assetPath);

  ui.Image? get(String assetPath) => _cache[assetPath];

  /// Carga una imagen de assets. Devuelve la imagen cacheada si ya existe.
  Future<ui.Image?> load(String assetPath) async {
    if (_cache.containsKey(assetPath)) return _cache[assetPath]!;
    if (_loading.containsKey(assetPath)) return _loading[assetPath]!.future;

    final completer = Completer<ui.Image?>();
    _loading[assetPath] = completer;

    try {
      final data = await rootBundle.load(assetPath);
      final codec = await ui.instantiateImageCodec(data.buffer.asUint8List());
      final frame = await codec.getNextFrame();
      _cache[assetPath] = frame.image;
      completer.complete(frame.image);
    } catch (e) {
      debugPrint('[SpriteLoader] Error: $assetPath → $e');
      completer.complete(null);
    } finally {
      _loading.remove(assetPath);
    }

    return completer.future;
  }

  /// Carga múltiples imágenes en paralelo. Ignora errores individuales.
  Future<void> loadAll(List<String> paths) async {
    for (final p in paths) {
      await load(p);
    }
    debugPrint('[SpriteLoader] Cargadas ${_cache.length}/${paths.length} imágenes');
  }

  void dispose() {
    for (final img in _cache.values) {
      img.dispose();
    }
    _cache.clear();
  }
}