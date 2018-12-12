package com.smarteyes.final_v1.UI;

import java.util.List;



import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.smarteyes.final_v1.R;


public class xDeviceListAdapter extends BaseAdapter {
		
	private Context context;
	private LayoutInflater mGroupInflater;
	public List<DevInfo> DataSource;
	
	
	public xDeviceListAdapter(Context c, List<DevInfo> DataSourceList) {
		this.context = c;
		
		this.mGroupInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.IconOnClickListener = IconOnClickListener;
		this.DataSource = DataSourceList;
		
		
	}
		
	private View.OnClickListener IconOnClickListener;

	


	

	@Override
	public int getCount() {
		int V = 0;
		if(this.DataSource != null)V = this.DataSource.size();
		return V;
	}

	@Override
	public Object getItem(int position) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getItemId(int position) {
		// TODO Auto-generated method stub
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		try{
			ItemHolder holder = null;
			if (convertView == null) {
				convertView = mGroupInflater.inflate(R.layout.item_devlist, null);
				holder = new ItemHolder();
			
				AbsListView.LayoutParams lp = new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);

				convertView.setLayoutParams(lp);
				convertView.setTag(holder);
				holder.N = (TextView) convertView.findViewById(R.id.devname_text);
				
			} else {
				holder = (ItemHolder)convertView.getTag();
			}
			DevInfo info = null;
			if(this.DataSource != null)info = this.DataSource.get(position);
			if (info != null && holder != null) {
				holder.N.setText(info.getDevid());
			}
			
		}catch (Exception e) {
			e.printStackTrace();
		}
		return convertView;
	}
}