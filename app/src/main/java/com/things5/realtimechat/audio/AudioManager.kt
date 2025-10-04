package com.things5.realtimechat.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Manages audio recording and playback for Realtime API
 * Uses PCM16 format at 24kHz as required by OpenAI
 */
class AudioManager {
    private val TAG = "AudioManager"
    
    // Audio configuration for OpenAI Realtime API
    private val SAMPLE_RATE = 24000 // 24kHz
    private val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Flow for audio data chunks (Base64 encoded)
    private val _audioChunks = MutableSharedFlow<String>(replay = 0)
    val audioChunks: SharedFlow<String> = _audioChunks.asSharedFlow()
    
    private var isRecording = false
    private var isPlaying = false
    
    /**
     * Start recording audio
     */
    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_FORMAT
            )
            
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size")
                return false
            }
            
            // Prova con VOICE_COMMUNICATION (EC, NS), altrimenti fallback a MIC
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord VOICE_COMMUNICATION non inizializzato, fallback a MIC")
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_IN,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord MIC non inizializzato")
                    return false
                }
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            recordingJob = scope.launch {
                // Usa un buffer di lettura più ampio per ridurre overhead
                val buffer = ByteArray(bufferSize * 2)
                var totalChunks = 0
                var lastVolumeLog = System.currentTimeMillis()
                
                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    
                    if (read > 0) {
                        totalChunks++
                        
                        // Calcola volume (RMS) per verificare che non sia silenzio
                        val rms = calculateRMS(buffer, read)
                        val volumeDb = if (rms > 0) 20 * kotlin.math.log10(rms.toDouble() / 32768.0) else -96.0
                        
                        // Log volume ogni secondo
                        val now = System.currentTimeMillis()
                        if (now - lastVolumeLog > 1000) {
                            Log.d(TAG, "🎤 Audio stats: RMS=$rms, Volume=${volumeDb.toInt()}dB, Chunks=$totalChunks")
                            lastVolumeLog = now
                            
                            // Avvisa se troppo silenzio
                            if (volumeDb < -60) {
                                Log.w(TAG, "⚠️ ATTENZIONE: Livello audio molto basso! Parla più vicino al microfono.")
                            }
                        }
                        
                        // Convert to Base64 and emit
                        val base64Audio = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                        _audioChunks.emit(base64Audio)
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "Invalid operation during recording")
                        break
                    }
                }
                
                Log.d(TAG, "✅ Recording stopped - Total chunks recorded: $totalChunks")
            }
            
            Log.d(TAG, "Recording started")
            return true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Recording permission not granted", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            return false
        }
    }
    
    /**
     * Stop recording audio
     */
    fun stopRecording() {
        if (!isRecording) {
            return
        }
        
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }
    
    /**
     * Initialize audio playback
     */
    fun initPlayback(): Boolean {
        if (isPlaying) {
            Log.w(TAG, "Already playing")
            return false
        }
        
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_OUT,
                AUDIO_FORMAT
            )
            
            if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size for playback")
                return false
            }
            
            // Usa un buffer 4x più grande per evitare underrun
            val bufferSize = minBufferSize * 4
            
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(CHANNEL_OUT)
                .build()
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack not initialized")
                return false
            }
            
            audioTrack?.play()
            isPlaying = true
            
            Log.d(TAG, "Playback initialized")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing playback", e)
            return false
        }
    }
    
    /**
     * Play audio chunk (Base64 encoded PCM16)
     */
    fun playAudio(base64Audio: String) {
        if (!isPlaying || audioTrack == null) {
            Log.w(TAG, "Playback not initialized")
            return
        }
        
        try {
            val audioData = Base64.decode(base64Audio, Base64.NO_WRAP)
            
            // Write the audio data to the track
            val written = audioTrack?.write(audioData, 0, audioData.size) ?: -1
            
            if (written < 0) {
                Log.e(TAG, "Error writing audio data: $written")
            } else if (written < audioData.size) {
                Log.w(TAG, "Partial write: $written / ${audioData.size} bytes")
            } else {
                Log.v(TAG, "Audio chunk played: ${audioData.size} bytes")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }
    
    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        if (!isPlaying) {
            return
        }
        
        isPlaying = false
        
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }
    
    /**
     * Calcola RMS (Root Mean Square) per misurare il volume
     */
    private fun calculateRMS(buffer: ByteArray, length: Int): Int {
        var sum = 0L
        var count = 0
        
        // PCM16 = 2 bytes per sample
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                // Leggi sample 16-bit (little-endian)
                val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                sum += (sample * sample).toLong()
                count++
            }
        }
        
        return if (count > 0) {
            kotlin.math.sqrt(sum.toDouble() / count).toInt()
        } else {
            0
        }
    }
    
    /**
     * Release all resources
     */
    fun release() {
        stopRecording()
        stopPlayback()
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = isPlaying
}
