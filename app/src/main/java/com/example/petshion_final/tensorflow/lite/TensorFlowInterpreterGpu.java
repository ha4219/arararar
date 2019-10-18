package com.example.petshion_final.tensorflow.lite;

import android.annotation.SuppressLint;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class TensorFlowInterpreterGpu implements Classifier {

    private final String TAG = TensorFlowInterpreterGpu.class.getSimpleName();

    private static final int MAX_RESULTS = 3;
    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;
    private static final float THRESHOLD = 0.1f;

    private static final int IMAGE_MEAN = 0;
    private static final float IMAGE_STD = 255.0f;

    private Interpreter interpreter;
    private int inputSize;
    private List<String> labelList;
    private boolean quant;

    /**
     * gpu version need this.
     */
    private static GpuDelegate gpuDelegate;

    private TensorFlowInterpreterGpu() {

    }

    public static Classifier create(AssetManager assetManager,
                                    String modelPath,
                                    String labelPath,
                                    int inputSize,
                                    boolean quant) throws IOException {

        gpuDelegate = new GpuDelegate();
        TensorFlowInterpreterGpu classifier = new TensorFlowInterpreterGpu();
        Interpreter.Options options = (new Interpreter.Options()).addDelegate(gpuDelegate);
        classifier.interpreter = new Interpreter(classifier.loadModelFile(
                assetManager, modelPath), options);
        classifier.labelList = classifier.loadLabelList(assetManager, labelPath);
        classifier.inputSize = inputSize;
        classifier.quant = quant;

        return classifier;
    }


    public List<Recognition> recognizeImageSorted(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);
        if(quant){
            byte[][] result = new byte[1][labelList.size()];
            interpreter.run(byteBuffer, result);
            return getSortedResultByte(result);
        } else {
            float [][] result = new float[1][labelList.size()];
            interpreter.run(byteBuffer, result);
            return getSortedResultFloat(result);
        }

    }

    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);
        if(quant){
            byte[][] result = new byte[1][labelList.size()];
            interpreter.run(byteBuffer, result);
            return getResultByte(result);
        } else {
            float [][] result = new float[1][labelList.size()];
            interpreter.run(byteBuffer, result);
            return getResultFloat(result);
        }
    }

    public byte[][] recognizeImageByByte(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);
        if(quant){
            byte[][] result = new byte[1][labelList.size()];
            interpreter.run(byteBuffer, result);
            return result;
        } else {
            byte [][] result = new byte[1][labelList.size()];
            interpreter.run(byteBuffer, result);
            return result;
        }
    }

    @Override
    public void close() {
        gpuDelegate.close();
        interpreter.close();
        interpreter = null;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;

        if(quant) {
            byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                final int val = intValues[pixel++];
                if(quant){
                    byteBuffer.put((byte) ((val >> 16) & 0xFF));
                    byteBuffer.put((byte) ((val >> 8) & 0xFF));
                    byteBuffer.put((byte) (val & 0xFF));
                } else {
                    byteBuffer.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    byteBuffer.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    byteBuffer.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                }

            }
        }
        return byteBuffer;
    }


    @SuppressLint("DefaultLocale")
    private List<Recognition> getResultByte(byte[][] labelProbArray) {
        ArrayList<Recognition> recognitions = new ArrayList<>();

        byte[] bytes = labelProbArray[0];
        for (int i = 0; i < bytes.length; i++) {
            float confidence = (labelProbArray[0][i] & 0xff) / 255.0f;

            Recognition recognition = new Recognition("" + i,
                    labelList.size() > i ? labelList.get(i) : "unknown",
                    confidence, quant);
            recognitions.add(recognition);
        }

        return recognitions;
    }

    @SuppressLint("DefaultLocale")
    private List<Recognition> getResultFloat(float[][] labelProbArray) {
        ArrayList<Recognition> recognitions = new ArrayList<>();

        float[] floats = labelProbArray[0];
        for (int i = 0; i < floats.length; i++) {
            float confidence = labelProbArray[0][i];

            Recognition recognition = new Recognition("" + i,
                    labelList.size() > i ? labelList.get(i) : "unknown",
                    confidence, quant);
            recognitions.add(recognition);
        }

        return recognitions;
    }

    @SuppressLint("DefaultLocale")
    private List<Recognition> getSortedResultByte(byte[][] labelProbArray) {

        PriorityQueue<Recognition> pq =
                new PriorityQueue<>(
                        MAX_RESULTS,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(Recognition lhs, Recognition rhs) {
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        for (int i = 0; i < labelList.size(); ++i) {
            float confidence = (labelProbArray[0][i] & 0xff) / 255.0f;
            if (confidence > THRESHOLD) {
                pq.add(new Recognition("" + i,
                        labelList.size() > i ? labelList.get(i) : "unknown",
                        confidence, quant));
            }
        }

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }

        return recognitions;
    }

    @SuppressLint("DefaultLocale")
    private List<Recognition> getSortedResultFloat(float[][] labelProbArray) {

        PriorityQueue<Recognition> pq =
                new PriorityQueue<>(
                        MAX_RESULTS,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(Recognition lhs, Recognition rhs) {
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        for (int i = 0; i < labelList.size(); ++i) {
            float confidence = labelProbArray[0][i];
            if (confidence > THRESHOLD) {
                pq.add(new Recognition("" + i,
                        labelList.size() > i ? labelList.get(i) : "unknown",
                        confidence, quant));
            }
        }

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }

        return recognitions;
    }
}