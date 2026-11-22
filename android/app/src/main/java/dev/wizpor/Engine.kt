package dev.wizpor

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt

/** Process-wide speech engine: loads Parakeet once, records audio, transcribes. */
object Engine {
    private const val TAG = "wizpor"
    const val SAMPLE_RATE = 16000

    @Volatile private var recognizer: OfflineRecognizer? = null
    private val loadLock = Any()

    @Volatile var level = 0f; private set
    @Volatile private var recording = false
    private var recordThread: Thread? = null
    private val chunks = ArrayList<ShortArray>()

    val isLoaded get() = recognizer != null

    fun preload(ctx: Context) {
        if (recognizer != null || !ModelManager.isReady(ctx)) return
        thread(name = "wizpor-load") { runCatching { ensureLoaded(ctx) }.onFailure { Log.e(TAG, "load failed", it) } }
    }

    private fun ensureLoaded(ctx: Context): OfflineRecognizer {
        recognizer?.let { return it }
        synchronized(loadLock) {
            recognizer?.let { return it }
            val dir = ModelManager.modelDir(ctx.applicationContext)
            val cfg = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = File(dir, "encoder.int8.onnx").path,
                        decoder = File(dir, "decoder.int8.onnx").path,
                        joiner = File(dir, "joiner.int8.onnx").path,
                    ),
                    tokens = File(dir, "tokens.txt").path,
                    modelType = "nemo_transducer",
                    numThreads = 4,
                ),
                decodingMethod = "greedy_search",
            )
            val t0 = System.currentTimeMillis()
            val r = OfflineRecognizer(null, cfg)
            Log.i(TAG, "Parakeet loaded in ${System.currentTimeMillis() - t0} ms")
            recognizer = r
            return r
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SAMPLE_RATE),
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord failed", e); return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        synchronized(chunks) { chunks.clear() }
        recording = true
        rec.startRecording()
        recordThread = thread(name = "wizpor-rec") {
            val buf = ShortArray(1600)
            try {
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) sum += buf[i] * buf[i].toDouble()
                    level = (sqrt(sum / n) / 32768.0).toFloat()
                    synchronized(chunks) { chunks.add(buf.copyOf(n)) }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                level = 0f
            }
        }
        return true
    }

    /** Stops recording and returns the captured audio as floats in [-1, 1]. */
    fun stopRecording(): FloatArray {
        recording = false
        recordThread?.join(1000)
        recordThread = null
        val all = synchronized(chunks) { chunks.toList().also { chunks.clear() } }
        val out = FloatArray(all.sumOf { it.size })
        var i = 0
        for (c in all) for (s in c) out[i++] = s / 32768f
        return out
    }

    fun cancel() { stopRecording() }

    /** True when every sample is (near) zero: Android silenced a background mic. */
    fun isSilentBlock(samples: FloatArray) = samples.isNotEmpty() && samples.all { abs(it) < 1e-6f }

    data class Result(val raw: String, val text: String, val ms: Long)

    /** Blocking; call off the main thread. */
    fun transcribe(ctx: Context, samples: FloatArray): Result {
        val t0 = System.currentTimeMillis()
        if (samples.size < SAMPLE_RATE * 3 / 10) return Result("", "", 0)
        val r = ensureLoaded(ctx)
        val stream = r.createStream()
        try {
            // Pad with a little silence so the last word is not clipped.
            stream.acceptWaveform(samples + FloatArray(SAMPLE_RATE / 2), SAMPLE_RATE)
            r.decode(stream)
            val raw = r.getResult(stream).text.trim()
            val prefs = Prefs(ctx)
            val text = Cleanup.tidy(raw, prefs.wordList, prefs.removeFillers)
            return Result(raw, text, System.currentTimeMillis() - t0)
        } finally {
            stream.release()
        }
    }
}
