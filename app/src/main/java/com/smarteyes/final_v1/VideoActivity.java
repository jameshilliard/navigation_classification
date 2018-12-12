package com.smarteyes.final_v1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import x1.Studio.Core.IVideoDataCallBack;
import x1.Studio.Core.OnlineService;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import android.view.Surface;
import android.widget.Toast;

import com.smarteyes.final_v1.UI.DevInfo;
import com.smarteyes.final_v1.env.BorderedText;
import com.smarteyes.final_v1.env.ImageUtils;
import com.smarteyes.final_v1.env.Logger;
import com.smarteyes.final_v1.Tracker;
import com.smarteyes.final_v1.tracking.MultiBoxTracker;

public  class  VideoActivity extends Activity implements SurfaceHolder.Callback,IVideoDataCallBack {

	private OnlineService ons;

	private Handler handler;
	private HandlerThread handlerThread;
	private Bitmap VideoBit;
	private boolean VideoInited = false;
	private Rect RectOfRegion;
	private RectF RectOfScale;
	private SurfaceView mSurfaceView;
	private SurfaceHolder mSurfaceHolder;
	private DevInfo devInfo;
	private String callID;
	private int type;
	private String tag ="VideoActivity";
	private Runnable postInferenceCallback;
	private Runnable imageConverter;
	protected int previewWidth = 0;
	protected int previewHeight = 0;



	private static final Logger LOGGER = new Logger();
	private static final String YOLO_MODEL_FILE = "file:///android_asset/yolo.pb";
	private static final int TF_OD_API_INPUT_SIZE = 300;
	private static final String TF_OD_API_MODEL_FILE =
			"file:///android_asset/ssd.pb";
	private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/list.txt";

	private static final int YOLO_INPUT_SIZE = 416;
	private static final String YOLO_INPUT_NAME = "input";
	private static final String YOLO_OUTPUT_NAMES = "output";
	private static final int YOLO_BLOCK_SIZE = 32;
	private static final float MINIMUM_CONFIDENCE_YOLO = 0.5f;
	private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
	private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
	private static final float TEXT_SIZE_DIP = 10;
	private Integer sensorOrientation;

	private Classifier detector;
	private long lastProcessingTimeMs;
	private Bitmap rgbFrameBitmap = null;
	private Bitmap croppedBitmap = null;
	private Bitmap cropCopyBitmap = null;

	private boolean computingDetection = false;

	private long timestamp = 0;

	private Matrix frameToCropTransform;
	private Matrix cropToFrameTransform;



	private TextToSpeech textToSpeech;
	private boolean isProcessingFrame = false;
	private byte[] yuvBytes;

	private enum DetectorMode {
		YOLO, TF_OD_API;
	}
	private static final DetectorMode MODE = DetectorMode.TF_OD_API;
	private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;

	private Tracker tracker;
	//MultiBoxTracker tracker;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ons = OnlineService.getInstance();
		ons.setCallBackData(this);
		int test = ons.regionVideoDataServer();//register callback
		mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView_video);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.addCallback(this);

		devInfo = 	(DevInfo) getIntent().getSerializableExtra("devInfo");
		type = devInfo.getType();//0 for lan

		if(devInfo!=null){
			if(type == 0){
				callID = ons.callLanVideo(devInfo.getDevid(),devInfo.getHkid(), devInfo.getVideoType(), devInfo.getChannal(),0);//呼叫局域网视频
			}else{
				Log.e(tag,"error while call video");
			}
		}

	}


	@Override
	public synchronized void onResume() {
		LOGGER.d("onResume " + this);
		super.onResume();

		handlerThread = new HandlerThread("inference");
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper());
	}


	@Override
	protected void onPause(){
		System.out.println("onPause");
		finish();
		super.onPause();

		handlerThread.quitSafely();
		try {
			handlerThread.join();
			handlerThread = null;
			handler = null;
		} catch (final InterruptedException e) {
			LOGGER.e(e, "Exception!");
		}

		if (textToSpeech != null) {
			textToSpeech.stop();
			textToSpeech.shutdown();
		}

	}





	//init view of player
	private void initPlayer(int mFrameWidth,int mFrameHeight){
		//640x360
		VideoInited = true;
		VideoBit =  Bitmap.createBitmap(mFrameWidth,
				mFrameHeight, Bitmap.Config.ARGB_8888);
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		LOGGER.d("mFrameWidth:%d       mFrameHeight:%d",mFrameWidth,mFrameHeight);
		initProcess(new Size(mFrameWidth,mFrameHeight),90);

		this.RectOfRegion = null;
		this.RectOfScale = new RectF(0, 0, mSurfaceView.getWidth(), mSurfaceView.getHeight());
		//1080x750
		LOGGER.e("surface:  %d   %d ",mSurfaceView.getWidth(),mSurfaceView.getHeight());
		//init speech
		this.textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
			@Override
			public void onInit(int status) {
				if (status == TextToSpeech.SUCCESS) {
					LOGGER.i("onCreate", "TextToSpeech is initialised");
				} else {
					LOGGER.e("onCreate", "Cannot initialise text to speech!");
				}
			}
		});
		tracker = new Tracker();
		tracker.init(300,300);
	}

	int[] rgbPixels;
	/*
	 * get the VideoBit and process
	 */
	@Override
	public void OnCallbackFunForDataServer(String CallId, ByteBuffer Buf,
										   int mFrameWidth, final int mFrameHeight, int mEncode, int mTime){
		if(!VideoInited){
			initPlayer(mFrameWidth, mFrameHeight);
		}

		//Log.i("OnCallbackFunForData","callVideoBit");
		this.VideoBit.copyPixelsFromBuffer(Buf);

		if (isProcessingFrame) {
			if(this.VideoBit != null && !this.VideoBit.isRecycled()){
				this.VideoBit.recycle();
				this.VideoBit = null;
			}
			System.gc();
			return;
		}

		isProcessingFrame = true;


		postInferenceCallback =
				new Runnable() {
					@Override
					public void run() {
						isProcessingFrame = false;
					}
				};

		final Canvas canvas = mSurfaceHolder.lockCanvas(null);
		canvas.drawColor(Color.BLACK);
		canvas.drawBitmap(this.VideoBit,
				this.RectOfRegion,this.RectOfScale, null);

		processImage();
//		trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
//		trackingOverlay.addCallback(
//				new OverlayView.DrawCallback() {
//					@Override
//					public void drawCallback(final Canvas canvas) {
//						LOGGER.e("333333");
//						tracker.draw(canvas);
//					}
//				});
		tracker.draw(canvas);
		//LOGGER.e("canvas: %d  %d",canvas.getWidth(),canvas.getHeight());
		this.mSurfaceHolder.unlockCanvasAndPost(canvas);


//		Bitmap test1= this.VideoBit;
//		intValues = new int[mFrameHeight * mFrameWidth];
//		byteValues = new byte[mFrameHeight * mFrameWidth * 3];
//		test1.getPixels(intValues, 0, mFrameWidth, 0, 0, mFrameWidth, mFrameHeight);

//
//		LOGGER.e("len %d",intValues.length);
//		for (int i = 0; i < intValues.length; ++i) {
////      byteValues[i * 3 + 2] = (byte) (intValues[i] & 0xFF);//BB
////      byteValues[i * 3 + 1] = (byte) ((intValues[i] >> 8) & 0xFF);//GG
////      byteValues[i * 3 + 0] = (byte) ((intValues[i] >> 16) & 0xFF);//RR
//        byteValues[i * 3 + 2] = (byte) ((intValues[i] & 0x1F)<<5);
//        byteValues[i * 3 + 1] = (byte) (((intValues[i] >> 5) & 0x3F)<<2);
//        byteValues[i * 3 + 0] = (byte) (((intValues[i] >> 11) & 0x1F)<<3);
////			int rr = (intValues[i] >> 11) & 0x1F;
////			int gg = (intValues[i] >> 5) & 0x3F;
////			int bb = intValues[i] & 0x1F;
////			byteValues[i * 3 + 2] = (byte) ((bb << 3) | (bb & 0x7));
////			byteValues[i * 3 + 1] = (byte) ((gg << 2) | (gg & 0x3));
////			byteValues[i * 3 + 0] = (byte) ((rr << 3) | (rr & 0x7));
//		}
//
//		MyBitmap myBitmap = new MyBitmap();
//		Bitmap newBitmap = myBitmap.createMyBitmap(byteValues,mFrameWidth,mFrameHeight);
//		Bitmap newBitmap=Bitmap.createBitmap(mFrameWidth,mFrameHeight, Bitmap.Config.RGB_565);
//		ByteBuffer buffer = ByteBuffer.wrap(byteValues);
//		newBitmap.copyPixelsFromBuffer(buffer);
//
//		Bitmap newBitmap=Bitmap.createBitmap(intValues,mFrameWidth,mFrameHeight,Bitmap.Config.ARGB_8888);
//		LOGGER.e("error!!!!!!!!!");
//		Canvas canvas = mSurfaceHolder.lockCanvas(null);
//		canvas.drawColor(Color.BLACK);
//		canvas.drawBitmap(newBitmap,
//				this.RectOfRegion,
//				this.RectOfScale, null);
//		this.mSurfaceHolder.unlockCanvasAndPost(canvas
//		final int width=mFrameWidth,height=mFrameHeight;
//		VideoBit.getPixels(rgbPixels, 0, mFrameWidth, 0, 0, mFrameWidth, mFrameHeight);
//		imageConverter =
//				new Runnable() {
//					@Override
//					public void run() {
//						ImageUtils.convertARGB8888ToYUV420SP1(rgbPixels
//								,yuvBytes,width,height);
//					}
//				};
	}

	OverlayView trackingOverlay;
	/*
	used for test
	 */
	public class MyBitmap{

		 public  Bitmap createMyBitmap(byte[] data, int width, int height){
			int []colors = convertByteToColor(data);
			if (colors == null){
				return null;
			}

			Bitmap bmp = Bitmap.createBitmap(colors, 0, width, width, height,
					Bitmap.Config.ARGB_8888);
			return bmp;
		}


		// 将一个byte数转成int
		// 实现这个函数的目的是为了将byte数当成无符号的变量去转化成int
		public  int convertByteToInt(byte data){

			int heightBit = (int) ((data>>4) & 0x0F);
			int lowBit = (int) (0x0F & data);
			return heightBit * 16 + lowBit;
		}


		// 将纯RGB数据数组转化成int像素数组
		public  int[] convertByteToColor(byte[] data){
			int size = data.length;
			if (size == 0){
				return null;
			}

			int arg = 0;
			if (size % 3 != 0){
				arg = 1;
			}

			// 一般情况下data数组的长度应该是3的倍数，这里做个兼容，多余的RGB数据用黑色0XFF000000填充
			int []color = new int[size / 3 + arg];
			int red, green, blue;

			if (arg == 0){
				for(int i = 0; i < color.length; ++i){
					red = convertByteToInt(data[i * 3]);
					green = convertByteToInt(data[i * 3 + 1]);
					blue = convertByteToInt(data[i * 3 + 2]);

					// 获取RGB分量值通过按位或生成int的像素值
					color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
				}
			}else{
				for(int i = 0; i < color.length - 1; ++i){
					red = convertByteToInt(data[i * 3]);
					green = convertByteToInt(data[i * 3 + 1]);
					blue = convertByteToInt(data[i * 3 + 2]);
					color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
				}

				color[color.length - 1] = 0xFF000000;
			}

			return color;
		}
	}






	/*
	 *the activity for detect
	*/


	public void initProcess(final Size size, final int rotation){
		final float textSizePx =
				TypedValue.applyDimension(
						TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
//		detector =
//				YOLO.create(
//						getAssets(),
//						YOLO_MODEL_FILE,
//						YOLO_INPUT_SIZE,
//						YOLO_INPUT_NAME,
//						YOLO_OUTPUT_NAMES,
//						YOLO_BLOCK_SIZE);
//		int cropSize = YOLO_INPUT_SIZE;
		int cropSize = TF_OD_API_INPUT_SIZE;
        try {
            detector = SSD_NET.create(
                    getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);

        } catch (final IOException e) {
            LOGGER.e("Exception initializing classifier!", e);
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            //System.out.println("error!!!!!");
            finish();
        }

		previewWidth = size.getWidth();
		previewHeight = size.getHeight();

		//sensorOrientation = rotation - getScreenOrientation();
		sensorOrientation=0;
		LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

		LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);

		//ARGB_8888:4X8位图像，最高
		rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
		//裁剪过的bmp, cropsize == input_size
		croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

		frameToCropTransform =
				ImageUtils.getTransformationMatrix(
						previewWidth, previewHeight,
						cropSize, cropSize,
						sensorOrientation, MAINTAIN_ASPECT);

		cropToFrameTransform = new Matrix();
		frameToCropTransform.invert(cropToFrameTransform);
		LOGGER.i("init detector!!!!!");

	}


	protected Bitmap getRgbBMP(){
		return this.VideoBit;
	}



	protected void readyForNextImage() {
		if (postInferenceCallback != null) {
			postInferenceCallback.run();
		}
	}

	public void processImage() {
		++timestamp;
		final long currTimestamp = timestamp;
		//tracker.init();
		//trackingOverlay.postInvalidate();


		if (computingDetection) {
			readyForNextImage();
			return;
		}

		computingDetection = true;
		LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");
		rgbFrameBitmap=getRgbBMP();

		readyForNextImage();

		final Canvas canvas1 = new Canvas(croppedBitmap);//croppedBitmap
		canvas1.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

		final Paint paint = new Paint();
		paint.setColor(Color.RED);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(2.0f);

		//LOGGER.e("croppedBitmap:   %d    %d",croppedBitmap.getHeight(),croppedBitmap.getWidth());
		runInBackground(
				new Runnable() {
					@Override
					public void run() {
						LOGGER.i("Running detection on image " + currTimestamp);
						final long startTime = SystemClock.uptimeMillis();
						final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
						lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

						//float minimumConfidence = MINIMUM_CONFIDENCE_YOLO;
						float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;


						final List<Classifier.Recognition> mappedRecognitions =
								new LinkedList<Classifier.Recognition>();

						for (final Classifier.Recognition result : results) {
							final RectF location = result.getLocation();

							if (location != null && result.getConfidence() >= minimumConfidence) {
								LOGGER.e("our confidence: %f   class: %s   location: %f   %f   %f   %f",
										result.getConfidence(),result.getTitle()
								,location.left,location.top,location.right,location.bottom);

								mappedRecognitions.add(result);
							}
						}
						if(!mappedRecognitions.isEmpty())
							tracker.get(mappedRecognitions);
							//LOGGER.e("aaaaaaaaaaaa");
						//trackingOverlay.postInvalidate();
						requestRender();
						initSpeak(mappedRecognitions);
						LOGGER.e("end speech");
						computingDetection = false;
					}
				});
	}



	private List<Classifier.Recognition> currentRecognitions;

	protected void initSpeak(List<Classifier.Recognition> recognitions) {
		if (recognitions.isEmpty() || textToSpeech.isSpeaking()) {
			currentRecognitions = Collections.emptyList();
			LOGGER.e("empty or speaking");
			return;
		}
		//LOGGER.e("speechTest222222222222");

		if (currentRecognitions != null) {

			// Ignore if current and new are same.
			if (currentRecognitions.equals(recognitions)) {
				LOGGER.e("equal speech");
				return;
			}
			final Set<Classifier.Recognition> intersection = new HashSet<>(recognitions);
			intersection.retainAll(currentRecognitions);

			// Ignore if new is sub set of the current
			if (intersection.equals(recognitions)) {
				return;
			}
		}
		//LOGGER.e("speechTest3333333333333");
		currentRecognitions = recognitions;
		speak();
	}

	private void speak() {
		int cropedWidth=TF_OD_API_INPUT_SIZE;
		int cropedHeight=TF_OD_API_INPUT_SIZE;
		if(MODE==DetectorMode.YOLO){
			cropedWidth=cropedHeight=YOLO_INPUT_SIZE;
		}

		final double rightStart = cropedWidth / 2 - 0.10 * cropedWidth;
		final double rightFinish = cropedWidth;
		final double leftStart = 0;
		final double leftFinish = cropedWidth / 2 + 0.10 * cropedWidth;
		final double previewArea = cropedWidth * cropedHeight;

		StringBuilder stringBuilder = new StringBuilder();
		for (int i = 0; i < currentRecognitions.size(); i++) {

			Classifier.Recognition recognition = currentRecognitions.get(i);
			stringBuilder.append(recognition.getTitle());

			float start = recognition.getLocation().top;
			float end = recognition.getLocation().bottom;
			double objArea = recognition.getLocation().width() * recognition.getLocation().height();


			if (objArea > previewArea / 2) {
				stringBuilder.append(" in front of you ");
			} else {


				if (start > leftStart && end < leftFinish) {
					stringBuilder.append(" on the left ");
				} else if (start > rightStart && end < rightFinish) {
					stringBuilder.append(" on the right ");
				} else {
					stringBuilder.append(" in front of you ");
				}
			}

			if (i + 1 < currentRecognitions.size()) {
				stringBuilder.append(" and ");
			}

		}
		stringBuilder.append(" detected.");
		textToSpeech.speak(stringBuilder.toString(), TextToSpeech.QUEUE_FLUSH, null);

	}






	@Override
	protected void onDestroy(){
		if(callID!=null)
			ons.closeLanVideo(callID);

		super.onDestroy();

	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
							   int height) {}


	@Override
	public void surfaceCreated(SurfaceHolder holder) {}


	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}

	@Override
	public void OnCallbackFunForRegionMonServer(int iFlag) {
		Log.v(tag, "OnCallbackFunForRegionMonServer");
	}

	@Override
	public void OnCallbackFunForGetItem(byte[] byteArray, int result) {
		Log.v(tag, "OnCallbackFunForGetItem");

	}


	@Override
	public void OnCallbackFunForLanDate(String devid, String devType, int hkid,
										int channal, int stats, String auioType) {
		Log.v(tag, "OnCallbackFunForLanDate");
	}


	@Override
	public void OnCallbackFunForUnDecodeDataServer(String CallId, byte[] Buf,
												   int mFrameWidth, int mFrameHeight, int mEncode, int mTime,int mFream) {
		Log.v(tag, "OnCallbackFunForUnDecodeDataServer");

	}

	@Override
	public void OnCallbackFunForIPRateData(String Ip) {
		// TODO Auto-generated method stub

	}

	@Override
	public void OnCallbackFunForComData(int Type, int Result,
										int AttachValueBufSize, String AttachValueBuf) {}


	protected synchronized void runInBackground(final Runnable r) {
		if (handler != null) {
			handler.post(r);
		}
	}


	public void addCallback(final OverlayView.DrawCallback callback) {
		final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
		if (overlay != null) {
			overlay.addCallback(callback);
		}
	}

	public void requestRender() {
		final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
		if (overlay != null) {
			overlay.postInvalidate();
		}
	}





}
