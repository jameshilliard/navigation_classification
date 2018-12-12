package com.smarteyes.final_v1;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import x1.Studio.Core.IVideoDataCallBack;
import x1.Studio.Core.OnlineService;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ListView;

import com.smarteyes.final_v1.UI.DevInfo;
import com.smarteyes.final_v1.UI.Global;
import com.smarteyes.final_v1.UI.xDeviceListAdapter;




public class StartActivity extends Activity implements IVideoDataCallBack{

	private OnlineService ons;
	private List<DevInfo> devInfoList;
	private xDeviceListAdapter lanListAdapter = null;
	private ListView devListView;
	private Handler handler;
	private DevInfo TerminalObjectForPlayActivity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_devlist);

		ons = OnlineService.getInstance();

		ons.setCallBackData(this);

		if(!Global.isInitLan){
			Global.isInitLan = true;
			ons.initLan();

		}

		handler = new Handler();

		devListView = (ListView)findViewById(R.id.devList);

		//button of refresh
		ImageButton c = (ImageButton) findViewById(R.id.refresh_btn);
		c.setOnClickListener(new ButtonListener());

		ons.refreshLan();
		ons.refreshLan();
		ons.refreshLan();
		ons.refreshLan();
		ons.refreshLan();
		devListView.setOnItemClickListener(new ListViewItemClickListener());

	}


	@Override
	protected void onResume() {
		ons.setCallBackData(this);
		super.onResume();
	}

	class ButtonListener implements OnClickListener{

		@Override
		public void onClick(View v) {
			 devInfoList = null;
			 ons.refreshLan();
		}

 	}

	class ListViewItemClickListener implements OnItemClickListener{

		@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int postion, long id) {
			TerminalObjectForPlayActivity =   devInfoList.get(postion);
			Intent intent = new Intent();
			//next step---VideoActivity
            Log.i("LanDev","first step");
			intent.setClass(StartActivity.this, VideoActivity.class);
			Bundle mBundle = new Bundle();
			mBundle.putSerializable("devInfo",
					StartActivity.this.TerminalObjectForPlayActivity);
			intent.putExtras(mBundle);
			startActivity(intent);
		}

	}



	@Override
	public void OnCallbackFunForLanDate(String devid, String videoType, int hkid,
			int channal, int status, String audioType) {

		if(devid.equals("302"))
			return;

		handler.post(new updateListView(devid, videoType, hkid, channal, status, audioType));

	}

	private class updateListView implements Runnable{

		String devid,devType,audioType;
		int hkid,channal,status;
		public updateListView(String devid,String devType,int hkid,int channal ,int status, String audioType){
			this.devid = devid;
			this.devType = devType;
			this.hkid = hkid;
			this.channal = channal;
			this.status = status;
			this.audioType = audioType;
		}
		@Override
		public void run() {
			setDevList(devid,  devType, hkid, channal, status, audioType);

		}

	}

	private void setDevList(String devid, String devType, int hkid,
			int channal, int stats, String audioType){

		DevInfo devInfo = new DevInfo();

		devInfo.setDevid(devid);
		devInfo.setVideoType(devType);
		devInfo.setHkid(hkid);
		devInfo.setChannal(channal);
		devInfo.setStats(stats);
		devInfo.setAudioType(audioType);
		devInfo.setType(0);
		createList(devInfo);

	}


	private void createList(DevInfo devInfo){

		if( devInfoList==null){
			 devInfoList = new ArrayList<DevInfo>();
		}

		devInfoList.add(devInfo);

		if (this.lanListAdapter == null) {

			this.lanListAdapter = new xDeviceListAdapter(this, devInfoList);
			this.devListView.setAdapter(lanListAdapter);

		} else {

			try{

				this.lanListAdapter.DataSource = devInfoList;
				this.lanListAdapter.notifyDataSetChanged();

				Log.v("DevList","notifyDataSetChanged..."+	this.lanListAdapter.DataSource.size());
			}catch(Exception e){
				e.printStackTrace();
			}

		}
	}



	@Override
	public void OnCallbackFunForRegionMonServer(int iFlag) {}


	@Override
	public void OnCallbackFunForGetItem(byte[] byteArray, int result) {}


	@Override
	public void OnCallbackFunForDataServer(String CallId, ByteBuffer Buf,
			int mFrameWidth, int mFrameHeight, int mEncode, int mTime) {}


	@Override
	public void OnCallbackFunForComData(int Type, int Result,
			int AttachValueBufSize, String AttachValueBuf) {}


	@Override
	public void OnCallbackFunForUnDecodeDataServer(String CallId, byte[] Buf,
			int mFrameWidth, int mFrameHeight, int mEncode, int mTime,int mFream) {}


	protected void onDestroy(){
		//ons.exitLan();
		super.onDestroy();
	}


	@Override
	public void OnCallbackFunForIPRateData(String Ip) {}



}
