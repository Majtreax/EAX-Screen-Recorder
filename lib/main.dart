import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:google_fonts/google_fonts.dart';
import 'dart:async';

void main() => runApp(const MyApp());

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'EAX Recorder',
      theme: ThemeData.dark().copyWith(
        textTheme: GoogleFonts.robotoTextTheme(
          ThemeData.dark().textTheme,
        ),
        appBarTheme: AppBarTheme(
          titleTextStyle: GoogleFonts.roboto(
            fontSize: 20,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
      home: const RecorderPage(),
    );
  }
}

class RecorderPage extends StatefulWidget {
  const RecorderPage({super.key});
  @override
  State<RecorderPage> createState() => _RecorderPageState();
}

class _RecorderPageState extends State<RecorderPage> {
  static const _channel = MethodChannel('ÆX Recorder');
  bool _isRecording = false;
  String? _error;
  Duration _recordingDuration = Duration.zero;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler(_handleCallbacks);
  }

  @override
  void dispose() {
    _timer?.cancel();
    _channel.setMethodCallHandler(null);
    super.dispose();
  }

  Future<void> _handleCallbacks(MethodCall call) async {
    if (!mounted) return;
    switch (call.method) {
      case 'ÆX RecorderOnStart':
        // Native start received; inform user
        _showMessage('Recording started.');
        break;
      case 'ÆX RecorderOnComplete':
        // Stop recording locally as well
        setState(() => _isRecording = false);
        _stopTimer();
        _showMessage('Recording saved.');
        break;
      case 'ÆX RecorderOnError':
        final e = call.arguments as Map;
        setState(() => _isRecording = false);
        _stopTimer();
        _showMessage('❌ Error. ${e['code']}: ${e['message']}');
        break;
      default:
        break;
    }
  }

  void _startTimer() {
    _timer?.cancel();
    setState(() {
      _recordingDuration = Duration.zero;
    });
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() => _recordingDuration += const Duration(seconds: 1));
    });
  }

  void _stopTimer() {
    _timer?.cancel();
    _timer = null;
  }

  String _format(Duration d) {
    String two(int n) => n.toString().padLeft(2, '0');
    final h = two(d.inHours);
    final m = two(d.inMinutes.remainder(60));
    final s = two(d.inSeconds.remainder(60));
    return '$h:$m:$s';
  }

  Future<void> _startRecording() async {
    if (_isRecording) {
      _showMessage('Already recording.');
      return;
    }

    final micGranted = await Permission.microphone.request().isGranted;
    if (!micGranted) {
      _showMessage('Microphone permission required.');
      return;
    }

    // Optimistic UI update
    setState(() => _isRecording = true);
    _startTimer();

    try {
      await _channel.invokeMethod('startRecording');
    } on PlatformException catch (e) {
      setState(() => _isRecording = false);
      _stopTimer();
      _showMessage('Failed to start: ${e.message}');
    }
  }

  Future<void> _stopRecording() async {
    if (!_isRecording) {
      _showMessage('No recording in progress.');
      return;
    }

    // Optimistic stop
    setState(() => _isRecording = false);
    _stopTimer();

    try {
      await _channel.invokeMethod('stopRecording');
    } on PlatformException catch (e) {
      _showMessage('Failed to stop: ${e.message}');
    }
  }

  void _showMessage(String msg) {
    final m = ScaffoldMessenger.of(context);
    m
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(msg,
              textAlign: TextAlign.center, style: GoogleFonts.roboto()),
          duration: const Duration(seconds: 2),
          behavior: SnackBarBehavior.floating,
          margin: EdgeInsets.only(
            bottom: MediaQuery.of(context).size.height * 0.1,
            left: MediaQuery.of(context).size.width * 0.25,
            right: MediaQuery.of(context).size.width * 0.25,
          ),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Center(child: Text('ÆX Recorder'))),
      body: SafeArea(
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: Text(_error!,
                      style: GoogleFonts.roboto(color: Colors.red)),
                ),
              if (_isRecording) ...[
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.fiber_manual_record,
                        color: Colors.deepPurpleAccent, size: 16),
                    const SizedBox(width: 8),
                    Text(_format(_recordingDuration),
                        style: GoogleFonts.roboto(fontSize: 24)),
                  ],
                ),
                const SizedBox(height: 16),
              ],
              ElevatedButton.icon(
                onPressed: _isRecording ? null : _startRecording,
                icon: const Icon(Icons.fiber_manual_record),
                label: Text(_isRecording ? 'Recording...' : 'Start Recording',
                    style: GoogleFonts.roboto(fontSize: 18)),
                style: ElevatedButton.styleFrom(
                  backgroundColor:
                      _isRecording ? Colors.grey : Colors.deepPurpleAccent,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
                ),
              ),
              const SizedBox(height: 16),
              ElevatedButton.icon(
                onPressed: _isRecording ? _stopRecording : null,
                icon: const Icon(Icons.stop),
                label: const Text('Stop Recording'),
                style: ElevatedButton.styleFrom(
                  backgroundColor:
                      _isRecording ? Colors.deepPurpleAccent : Colors.grey,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
