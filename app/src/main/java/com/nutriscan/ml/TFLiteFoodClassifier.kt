package com.nutriscan.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TensorFlow Lite food classifier.
 */
@Singleton
class TFLiteFoodClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) : FoodClassificationService {
    
    companion object {
        private const val TAG = "TFLiteFoodClassifier"
        
        private const val MODEL_PATH = "ml/food11.tflite"
        private const val LABELS_PATH = "ml/food11_labels.txt"
        
        // Thresholds (in 0-1 scale after normalization)
        private const val MIN_CONFIDENCE = 0.05f
        private const val HIGH_CONFIDENCE = 0.7f
    }
    
    override val classifierName = "TFLite Food Classifier"
    override val isFoodTrained = true
    
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var isInitialized = false
    
    // Model shape info
    private var inputWidth = 192
    private var inputHeight = 192
    private var inputChannels = 3
    private var inputDataType: DataType = DataType.UINT8
    private var outputDataType: DataType = DataType.UINT8
    private var outputSize = 0
    
    @Synchronized
    private fun initialize() {
        if (isInitialized) return
        
        try {
            Log.d(TAG, "Loading model from: $MODEL_PATH")
            
            val modelBuffer = loadModelFile()
            Log.d(TAG, "Model loaded, size: ${modelBuffer.capacity()} bytes")
            
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            
            val inputTensor = interpreter!!.getInputTensor(0)
            val outputTensor = interpreter!!.getOutputTensor(0)
            
            val inputShape = inputTensor.shape()
            val outputShape = outputTensor.shape()
            
            inputDataType = inputTensor.dataType()
            outputDataType = outputTensor.dataType()
            
            Log.d(TAG, "Input shape: ${inputShape.contentToString()}, dtype: $inputDataType")
            Log.d(TAG, "Output shape: ${outputShape.contentToString()}, dtype: $outputDataType")
            
            if (inputShape.size >= 4) {
                inputHeight = inputShape[1]
                inputWidth = inputShape[2]
                inputChannels = inputShape[3]
            }
            
            outputSize = if (outputShape.size >= 2) outputShape[1] else outputShape[0]
            
            Log.d(TAG, "Parsed - Input: ${inputWidth}x${inputHeight}x$inputChannels ($inputDataType), Output: $outputSize ($outputDataType)")
            
            labels = loadLabels()
            Log.d(TAG, "Loaded ${labels.size} labels")
            
            isInitialized = true
            Log.d(TAG, "Initialization complete!")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLite", e)
            throw e
        }
    }
    
    override suspend fun classifyFood(bitmap: Bitmap): FoodClassificationResult {
        return try {
            initialize()
            
            val interp = interpreter ?: return FoodClassificationResult.error()
            
            Log.d(TAG, "Classifying image: ${bitmap.width}x${bitmap.height}")
            
            val inputBuffer = preprocessBitmap(bitmap)
            Log.d(TAG, "Input buffer ready: ${inputBuffer.capacity()} bytes")
            
            // Output buffer must match model output type
            val results = when (outputDataType) {
                DataType.UINT8 -> {
                    val outputArray = Array(1) { ByteArray(outputSize) }
                    Log.d(TAG, "Running inference (UINT8 output)...")
                    interp.run(inputBuffer, outputArray)
                    // Convert UINT8 [0-255] to Float [0-1]
                    outputArray[0].map { (it.toInt() and 0xFF) / 255f }
                }
                DataType.FLOAT32 -> {
                    val outputArray = Array(1) { FloatArray(outputSize) }
                    Log.d(TAG, "Running inference (FLOAT32 output)...")
                    interp.run(inputBuffer, outputArray)
                    outputArray[0].toList()
                }
                else -> {
                    Log.e(TAG, "Unsupported output type: $outputDataType")
                    return FoodClassificationResult.error()
                }
            }
            
            Log.d(TAG, "Inference complete, parsing ${results.size} outputs")
            
            val classificationResults = parseOutput(results)
            
            Log.d(TAG, "Top predictions: ${classificationResults.take(5).map { "${it.label}(${it.confidencePercent}%)" }}")
            
            when {
                classificationResults.isEmpty() -> {
                    Log.w(TAG, "No results above threshold")
                    FoodClassificationResult.noFood()
                }
                classificationResults.first().confidence >= HIGH_CONFIDENCE -> {
                    FoodClassificationResult(
                        results = classificationResults,
                        status = ClassificationStatus.HIGH_CONFIDENCE
                    )
                }
                classificationResults.size == 1 -> {
                    FoodClassificationResult(
                        results = classificationResults,
                        status = ClassificationStatus.SINGLE_MATCH
                    )
                }
                else -> {
                    FoodClassificationResult(
                        results = classificationResults,
                        status = ClassificationStatus.MULTIPLE_CANDIDATES
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed: ${e.message}", e)
            FoodClassificationResult.error()
        }
    }
    
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }
    
    private fun loadLabels(): List<String> {
        return context.assets.open(LABELS_PATH).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.toList()
        }
    }
    
    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        
        val bytesPerChannel = if (inputDataType == DataType.UINT8) 1 else 4
        val bufferSize = inputWidth * inputHeight * inputChannels * bytesPerChannel
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(inputWidth * inputHeight)
        resized.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        
        if (inputDataType == DataType.UINT8) {
            for (pixelValue in intValues) {
                byteBuffer.put(((pixelValue shr 16) and 0xFF).toByte())
                byteBuffer.put(((pixelValue shr 8) and 0xFF).toByte())
                byteBuffer.put((pixelValue and 0xFF).toByte())
            }
        } else {
            for (pixelValue in intValues) {
                val r = (pixelValue shr 16) and 0xFF
                val g = (pixelValue shr 8) and 0xFF
                val b = pixelValue and 0xFF
                byteBuffer.putFloat((r - 127.5f) / 127.5f)
                byteBuffer.putFloat((g - 127.5f) / 127.5f)
                byteBuffer.putFloat((b - 127.5f) / 127.5f)
            }
        }
        
        byteBuffer.rewind()
        return byteBuffer
    }
    
    private fun parseOutput(output: List<Float>): List<ClassificationResult> {
        val topIndices = output.indices.sortedByDescending { output[it] }.take(5)
        Log.d(TAG, "Top 5 raw: ${topIndices.map { "[$it]=${String.format("%.3f", output[it])}" }}")
        
        return output.mapIndexed { index, confidence ->
            val label = if (index < labels.size) labels[index] else "Unknown_$index"
            if (label == "__background__" || label.startsWith("/g/") || label.startsWith("/m/")) {
                null
            } else {
                ClassificationResult(
                    label = label,
                    confidence = confidence,
                    index = index
                )
            }
        }
            .filterNotNull()
            .filter { it.confidence >= MIN_CONFIDENCE }
            .sortedByDescending { it.confidence }
            .take(10)
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }
}
