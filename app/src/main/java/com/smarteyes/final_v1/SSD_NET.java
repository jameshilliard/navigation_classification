
package com.smarteyes.final_v1;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Trace;
import android.view.SurfaceHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;
import org.tensorflow.Graph;
import org.tensorflow.Operation;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;
import com.smarteyes.final_v1.env.Logger;


public class SSD_NET implements Classifier {
  private static final Logger LOGGER = new Logger();

  // Only return this many results.
  private static final int MAX_RESULTS = 100;

  // Config values.
  private String inputName;
  private int inputSize;

  // Pre-allocated buffers.
  private Vector<String> labels = new Vector<String>();
  private int[] intValues;
  private byte[] byteValues;
  private float[] outputLocations;
  private float[] outputScores;
  private float[] outputClasses;
  private float[] outputNumDetections;
  private String[] outputNames;

  private boolean logStats = false;

  private TensorFlowInferenceInterface inferenceInterface;

  public static Classifier create(
      final AssetManager assetManager,
      final String modelFilename,
      final String labelFilename,
      final int inputSize) throws IOException {
    final SSD_NET d = new SSD_NET();

    InputStream labelsInput = null;
    String actualFilename = labelFilename.split("file:///android_asset/")[1];
    labelsInput = assetManager.open(actualFilename);
    BufferedReader br = null;
    br = new BufferedReader(new InputStreamReader(labelsInput));
    String line;
    while ((line = br.readLine()) != null) {
      LOGGER.w(line);
      d.labels.add(line);
    }
    br.close();


    d.inferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFilename);

    final Graph g = d.inferenceInterface.graph();

    d.inputName = "image_tensor";
    // The inputName node has a shape of [N, H, W, C], where
    // N is the batch size
    // H = W are the height and width
    // C is the number of channels (3 for our purposes - RGB)
    final Operation inputOp = g.operation(d.inputName);
    if (inputOp == null) {
      throw new RuntimeException("Failed to find input Node '" + d.inputName + "'");
    }
    d.inputSize = inputSize;
    // The outputScoresName node has a shape of [N, NumLocations], where N
    // is the batch size.
    final Operation outputOp1 = g.operation("detection_scores");
    if (outputOp1 == null) {
      throw new RuntimeException("Failed to find output Node 'detection_scores'");
    }
    final Operation outputOp2 = g.operation("detection_boxes");
    if (outputOp2 == null) {
      throw new RuntimeException("Failed to find output Node 'detection_boxes'");
    }
    final Operation outputOp3 = g.operation("detection_classes");
    if (outputOp3 == null) {
      throw new RuntimeException("Failed to find output Node 'detection_classes'");
    }

    // Pre-allocate buffers.
    d.outputNames = new String[] {"detection_boxes", "detection_scores",
                                  "detection_classes", "num_detections"};
    d.intValues = new int[d.inputSize * d.inputSize];
    d.byteValues = new byte[d.inputSize * d.inputSize * 3];
    d.outputScores = new float[MAX_RESULTS];
    d.outputLocations = new float[MAX_RESULTS * 4];
    d.outputClasses = new float[MAX_RESULTS];
    d.outputNumDetections = new float[1];
    return d;
  }

  private SSD_NET() {}

  @Override
  public List<Recognition> recognizeImage(final Bitmap bitmap) {
    // Log this method so that it can be analyzed with systrace.
    Trace.beginSection("recognizeImage");

    Trace.beginSection("preprocessBitmap");
    // Preprocess the image data to extract R, G and B bytes from int of form 0x00RRGGBB
    // on the provided parameters.
    bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
   // LOGGER.e("alpha???:  %d",intValues[0]);
    for (int i = 0; i < intValues.length; ++i) {
      byteValues[i * 3 + 2] = (byte) (intValues[i] & 0xFF);//BB
      byteValues[i * 3 + 1] = (byte) ((intValues[i] >> 8) & 0xFF);//GG
      byteValues[i * 3 + 0] = (byte) ((intValues[i] >> 16) & 0xFF);//RR
//      LOGGER.e("test of value: %d ", intValues[i]);

//        byteValues[i * 3 + 2] = (byte) (intValues[i] & 0x1F);
//        byteValues[i * 3 + 1] = (byte) ((intValues[i] >> 5) & 0x3F);
//        byteValues[i * 3 + 0] = (byte) ((intValues[i] >> 11) & 0x1F);
//        int rr = (intValues[i] >> 11) & 0x1F;
//        int gg = (intValues[i] >> 5) & 0x3F;
//        int bb = intValues[i] & 0x1F;
//        byteValues[i * 3 + 2] = (byte) ((bb << 3) | (bb & 0x7));
//        byteValues[i * 3 + 1] = (byte) ((gg << 2) | (gg & 0x3));
//        byteValues[i * 3 + 0] = (byte) ((rr << 3) | (rr & 0x7));
//        LOGGER.e("alpha???:  %d",(intValues[i] >> 16) & 0xFF);
//      if((intValues[i]>>26)!=0){
//                LOGGER.e("alpha???:  %d  %d  %d  %d  %d  %d  %d  %d ",(intValues[i]>>8)&(1)
//                        ,(intValues[i]>>9)&(1),(intValues[i]>>10)&(1),(intValues[i]>>11)&(1)
//                ,(intValues[i]>>12)&(1),(intValues[i]>>13)&(1),(intValues[i]>>14)&(1)
//                ,(intValues[i]>>15)&(1));
//
//      }
    }

    Trace.endSection(); // preprocessBitmap

    // Copy the input data into TensorFlow.
    Trace.beginSection("feed");
    inferenceInterface.feed(inputName, byteValues, 1, inputSize, inputSize, 3);
    Trace.endSection();

    // Run the inference call.
    Trace.beginSection("run");
    inferenceInterface.run(outputNames, logStats);
    Trace.endSection();

    // Copy the output Tensor back into the output array.
    Trace.beginSection("fetch");
    outputLocations = new float[MAX_RESULTS * 4];
    outputScores = new float[MAX_RESULTS];
    outputClasses = new float[MAX_RESULTS];
    outputNumDetections = new float[1];
    inferenceInterface.fetch(outputNames[0], outputLocations);
    inferenceInterface.fetch(outputNames[1], outputScores);
    inferenceInterface.fetch(outputNames[2], outputClasses);
    inferenceInterface.fetch(outputNames[3], outputNumDetections);
    Trace.endSection();

    // Find the best detections.
    final PriorityQueue<Recognition> pq =
        new PriorityQueue<Recognition>(
            1,
            new Comparator<Recognition>() {
              @Override
              public int compare(final Recognition lhs, final Recognition rhs) {
                // Intentionally reversed to put high confidence at the head of the queue.
                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
              }
            });

    // Scale them back to the input size.
    for (int i = 0; i < outputScores.length; ++i) {
      final RectF detection =
          new RectF(
              outputLocations[4 * i + 1] * inputSize,
              outputLocations[4 * i] * inputSize,
              outputLocations[4 * i + 3] * inputSize,
              outputLocations[4 * i + 2] * inputSize);
//      if(outputLocations[4*i]!=0){
//        LOGGER.e("raw location: %f   %f    %f    %f ",outputLocations[4 * i + 1]
//                ,outputLocations[4 * i],outputLocations[4 * i + 3],outputLocations[4 * i + 2]);
//      }
      pq.add(
          new Recognition("" + i, labels.get((int) outputClasses[i]), outputScores[i], detection));
    }

    final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
    for (int i = 0; i < Math.min(pq.size(), MAX_RESULTS); ++i) {
      recognitions.add(pq.poll());
    }
    Trace.endSection(); // "recognizeImage"
    return recognitions;
  }

  @Override
  public void enableStatLogging(final boolean logStats) {
    this.logStats = logStats;
  }

  @Override
  public String getStatString() {
    return inferenceInterface.getStatString();
  }

  @Override
  public void close() {
    inferenceInterface.close();
  }

  public void draw(SurfaceHolder mSurfaceHolder){
    Bitmap newmap= Bitmap.createBitmap(intValues,640,360,Bitmap.Config.RGB_565);
    Canvas canvas = mSurfaceHolder.lockCanvas(null);
		canvas.drawColor(Color.BLACK);
		canvas.drawBitmap(newmap,
				null,
				new RectF(0,0,1080,750), null);
		mSurfaceHolder.unlockCanvasAndPost(canvas);


  }
}
