package com.smarteyes.final_v1;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;

import com.smarteyes.final_v1.env.BorderedText;
import com.smarteyes.final_v1.env.ImageUtils;
import com.smarteyes.final_v1.Classifier.Recognition;
import com.smarteyes.final_v1.env.Logger;

import java.util.LinkedList;
import java.util.List;

import static android.graphics.Color.BLUE;

public class Tracker {
    private List<Classifier.Recognition> presults;
    private final Paint paint = new Paint();
    private static final Logger LOGGER = new Logger();
    private int frameHeight,frameWidth;
    private Matrix frameToCanvasMatrix;



    public synchronized void draw(final Canvas canvas) {
        final float multiplier =
                Math.min(canvas.getHeight() / (float) frameHeight,
                        canvas.getWidth() / (float) frameWidth);
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
//                        (int) (multiplier * frameWidth),
//                        (int) (multiplier * frameHeight),
                        canvas.getWidth(),canvas.getHeight(),
                        0,
                        false);
        //LOGGER.e("canvas: %d %d",canvas.getHeight(),canvas.getWidth());

        if(presults!=null)
            for(final Classifier.Recognition recognition:presults){
                RectF location=recognition.getLocation();
                paint.setColor(Color.BLUE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(10.0f);
                getFrameToCanvasMatrix().mapRect(location);

                canvas.drawRect(location,paint);
                final float textsize=37;
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(textsize/8);
                paint.setTextSize(textsize);
                final String labelString =
                        String.format("%s %.2f", recognition.getTitle(), recognition.getConfidence());
                canvas.drawText(labelString,location.left,location.bottom,paint);
//            final float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
//            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
//
//            final String labelString =
//                    !TextUtils.isEmpty(recognition.title)
//                            ? String.format("%s %.2f", recognition.title, recognition.detectionConfidence)
//                            : String.format("%.2f", recognition.detectionConfidence);
//            borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.bottom, labelString);
            }
    }

    public synchronized void init(int height,int width){
        this.frameHeight=height;
        this.frameWidth=width;
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public synchronized void get(final List<Classifier.Recognition> results){
        //LOGGER.e("test results");
        presults=results;
    }
}
