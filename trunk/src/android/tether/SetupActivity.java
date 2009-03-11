/**
 *  This software is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Use this application at your own risk.
 *
 *  Copyright (c) 2009 by Harald Mueller and Seth Lemons.
 */

package android.tether;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import android.app.ListActivity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.tether.data.ClientData;
import android.tether.system.CoreTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class SetupActivity extends ListActivity {
	
	private static final String DATA_FILE_PATH = "/data/data/android.tether";
    private static ArrayList<ClientData> clientDataList = new ArrayList<ClientData>();
    
    private ImageButton saveBtn;
    private ImageButton refreshBtn;
    private CheckBox checkBoxAccess;
    private EditText SSIDText;
    private Spinner ChanSpin;
    
    private EfficientAdapter efficientAdapter;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setupview);

        // Save-Button
        this.saveBtn = (ImageButton)findViewById(R.id.ImgBtnSave);
		this.saveBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d("*** DEBUG ***", "SaveBtn pressed ...");
				ArrayList<String> whitelist = new ArrayList<String>();
				for (ClientData tmpClientData : clientDataList) {
					if (tmpClientData.isAccessAllowed()) {
						whitelist.add(tmpClientData.getMacAddress());
					}
				}
				if (SetupActivity.this.checkBoxAccess.isChecked()) {
					try {
						CoreTask.saveWhitelist(whitelist);
						if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(DATA_FILE_PATH+"/bin/dnsmasq")) {
							SetupActivity.this.restartSecuredWifi();
						}
					}
					catch (Exception ex) {
						SetupActivity.this.displayToastMessage("Unable to save whitelist-file!");
					}
				}
				else {
					if (CoreTask.whitelistExists()) {
						if (!CoreTask.removeWhitelist()) {
							SetupActivity.this.displayToastMessage("Unable to remove whitelist-file!");
						}
					}
				}
				//update SSID if it's changed
				if (!SetupActivity.this.getSSID().equals(SetupActivity.this.SSIDText.getText().toString())){
					try {
						SetupActivity.this.setSSID();
						if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(DATA_FILE_PATH+"/bin/dnsmasq")) {
							SetupActivity.this.displayToastMessage("New SSID will be used once tethering is stopped and restarted.");
						}
					}
					catch (Exception ex) {
						SetupActivity.this.displayToastMessage("Unable to save new SSID!");
					}
				}
				//update channel if it's changed
				if (!SetupActivity.this.getChan().equals(SetupActivity.this.ChanSpin.getSelectedItem().toString())){
					try {
						SetupActivity.this.setChan();
						if (CoreTask.isNatEnabled() && CoreTask.isProcessRunning(DATA_FILE_PATH+"/bin/dnsmasq")) {
							SetupActivity.this.displayToastMessage("New channel will be used once tethering is stopped and restarted.");
						}
					}
					catch (Exception ex) {
						SetupActivity.this.displayToastMessage("Unable to save new channel!");
					}
				}
				SetupActivity.this.finish();
			}
		});
		
		// Refresh-Button
		this.refreshBtn = (ImageButton)findViewById(R.id.ImgBtnRefresh);
		this.refreshBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d("*** DEBUG ***", "RefreshBtn pressed ...");
				SetupActivity.this.updateListView();
				SetupActivity.this.efficientAdapter.update();
			}
		});

		this.updateListView();
        this.efficientAdapter = new EfficientAdapter(this);
		this.setListAdapter(this.efficientAdapter);
    }
	
	private void updateListView() {
		this.displayToastMessage("Refreshing client-list ...");
        // clientData
        clientDataList = new ArrayList<ClientData>();
        ArrayList<String> whitelist = null;
        try {
			whitelist = CoreTask.getWhitelist();
		} catch (Exception e) {
			SetupActivity.this.displayToastMessage("Unable to read whitelist-file!");
		}
        Hashtable<String,ClientData> leases = null;
        try {
			leases = CoreTask.getLeases();
		} catch (Exception e) {
			SetupActivity.this.displayToastMessage("Unable to read leases-file!");
		}
        if (whitelist != null) {
	        for (String macAddress : whitelist) {
	        	ClientData clientData = new ClientData();
	        	clientData.setConnected(false);
	        	clientData.setIpAddress("- Not connected -");
	        	if (leases.containsKey(macAddress)) {
	        		clientData = leases.get(macAddress);
	            	Log.d("*** DEBUG ***", clientData.isConnected()+" - "+clientData.getIpAddress());
	        		leases.remove(macAddress);
	        	}
	        	clientData.setAccessAllowed(true);
	        	clientData.setMacAddress(macAddress);
	        	clientDataList.add(clientData);
	        }
        }
        if (leases != null) {
	        Enumeration<String> enumLeases = leases.keys();
	        while (enumLeases.hasMoreElements()) {
	        	String macAddress = enumLeases.nextElement();
	        	clientDataList.add(leases.get(macAddress));
	        }
        }
        
        for (ClientData tmpClientData : clientDataList) {
        	Log.d("*** DEBUG ***", tmpClientData.getMacAddress()+" -- "+tmpClientData.getClientName()+" -- "+tmpClientData.getIpAddress());
        }
        
        // Checkbox-Access
        this.checkBoxAccess = (CheckBox)findViewById(R.id.checkBoxAccess);
        this.checkBoxAccess.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				Log.d("*** DEBUG ***", ">>> "+arg0.toString()+" - >>> "+arg1);
			}
        });
        if (CoreTask.whitelistExists()) {
        	this.checkBoxAccess.setChecked(true);
        }
        
        //SSID
        this.SSIDText = (EditText)findViewById(R.id.SSID);
        this.updateSSIDText();
        
        //Channel
        this.ChanSpin = (Spinner)findViewById(R.id.Chan);
        this.ChanSpin.setAdapter(new ArrayAdapter(this,
        		android.R.layout.simple_spinner_item,
        		new String[] { "1","2","3","4","5","6","7","8","9","10","11","12","13","14" }));
        this.updateChanSelection();
    }
	
	public void updateSSIDText(){
		this.SSIDText.setText((CharSequence)this.getSSID());
        this.SSIDText.invalidate();
	}
    
    public String getSSID(){
    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
    	File inFile = new File(filename);
    	String currSSID = "undefined";
    	try{
        	InputStream is = new FileInputStream(inFile);
        	BufferedReader br = new BufferedReader(new InputStreamReader(is));
    		String s = br.readLine();
	    	while (s!=null){
	    		if (s.contains("dot11DesiredSSID")){
	    			currSSID = s.substring(s.indexOf("= ")+2).trim();
	    			return currSSID;
	    		}
	    		s = br.readLine();
	    	}
	    	is.close();
	    	br.close();
    	}
    	catch (Exception e){
    		//Nothing
    	}
    	return currSSID;
    }
    
    public void setSSID(){
    	String newSSID = this.SSIDText.getText().toString();
    	if (newSSID.contains("#") || newSSID.contains("`")){
    		SetupActivity.this.displayToastMessage("New SSID cannot contain '#' or '`'!");
    		this.SSIDText.setText(this.getSSID());
    		return;
    	}
    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
    	String fileString = "";
    	String s;
        try {
        	File inFile = new File(filename);
        	BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
        	while((s = br.readLine())!=null) {
        		if (s.contains("dot11DesiredSSID")){
	    			s = "dot11DesiredSSID = "+newSSID;
	    		}
        		s+="\n";
        		fileString += s;
			}
        	File outFile = new File(filename);
        	OutputStream out = new FileOutputStream(outFile);
        	out.write(fileString.getBytes());
        	out.close();
        	br.close();
		} catch (IOException e) {
			this.displayToastMessage("Couldn't install file - "+filename+"!");
		}
    }
    
    public void updateChanSelection(){
    	this.ChanSpin.setSelection(new Integer(this.getChan()) - 1);
        this.ChanSpin.invalidate();
	}
    
    public String getChan(){
    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
    	File inFile = new File(filename);
    	String currChan = "6";
    	try{
        	InputStream is = new FileInputStream(inFile);
        	BufferedReader br = new BufferedReader(new InputStreamReader(is));
    		String s = br.readLine();
	    	while (s!=null){
	    		if (s.contains("dot11DesiredChannel")){
	    			currChan = s.substring(s.indexOf("= ")+2).trim();
	    			return currChan;
	    		}
	    		s = br.readLine();
	    	}
	    	is.close();
	    	br.close();
    	}
    	catch (Exception e){
    		//Nothing
    	}
    	return currChan;
    }
    
    public void setChan(){
    	String filename = DATA_FILE_PATH+"/conf/tiwlan.ini";
    	String fileString = "";
    	String s;
        try {
        	File inFile = new File(filename);
        	BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
        	while((s = br.readLine())!=null) {
        		if (s.contains("dot11DesiredChannel")){
	    			s = "dot11DesiredChannel = "+this.ChanSpin.getSelectedItem().toString();
	    		}
        		s+="\n";
        		fileString += s;
			}
        	File outFile = new File(filename);
        	OutputStream out = new FileOutputStream(outFile);
        	out.write(fileString.getBytes());
        	out.close();
        	br.close();
		} catch (IOException e) {
			this.displayToastMessage("Couldn't install file - "+filename+"!");
		}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu installBinaries = menu.addSubMenu(0, 0, 0, getString(R.string.installtext));
    	installBinaries.setIcon(R.drawable.install);
    	return supRetVal;
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d("*** DEBUG ***", "Menuitem:getId  -  "+menuItem.getItemId()); 
    	if (menuItem.getItemId() == 0) {
    		SetupActivity.this.installBinaries();
    	}
    	return supRetVal;
    } 
    
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		ClientData clientData = clientDataList.get(position);
		clientData.setAccessAllowed(!clientData.isAccessAllowed());
		clientDataList.set(position, clientData);
		//setListAdapter(new EfficientAdapter(this));
		this.updateListView();
        Log.d("*** DEBUG ***", "ListEntry selected - "+position); 
	}
	
    private void installBinaries() {
    	List<String> filenames = new ArrayList<String>();
    	// tether
    	this.copyBinary(DATA_FILE_PATH+"/bin/tether", R.raw.tether);
    	filenames.add("tether");
    	// dnsmasq
    	this.copyBinary(DATA_FILE_PATH+"/bin/dnsmasq", R.raw.dnsmasq);
    	filenames.add("dnsmasq");
    	// iptables
    	this.copyBinary(DATA_FILE_PATH+"/bin/iptables", R.raw.iptables);
    	filenames.add("iptables");
    	try {
			CoreTask.chmodBin(filenames);
		} catch (Exception e) {
			this.displayToastMessage("Unable to change permission on binary files!");
		}
    	// dnsmasq.conf
    	this.copyBinary(DATA_FILE_PATH+"/conf/dnsmasq.conf", R.raw.dnsmasq_conf);
    	// tiwlan.ini
    	this.copyBinary(DATA_FILE_PATH+"/conf/tiwlan.ini", R.raw.tiwlan_ini);
    	this.displayToastMessage("Binaries and config-files installed!");
    	this.updateSSIDText();
    	this.updateChanSelection();
    }
    
    private void copyBinary(String filename, int resource) {
    	File outFile = new File(filename);
    	InputStream is = this.getResources().openRawResource(resource);
    	byte buf[]=new byte[1024];
        int len;
        try {
        	OutputStream out = new FileOutputStream(outFile);
        	while((len = is.read(buf))>0) {
				out.write(buf,0,len);
			}
        	out.close();
        	is.close();
		} catch (IOException e) {
			this.displayToastMessage("Couldn't install file - "+filename+"!");
		}
    }
    
    private void restartSecuredWifi() {
    	if (!CoreTask.runRootCommand(DATA_FILE_PATH+"/bin/tether restartsecwifi")) {
    		this.displayToastMessage("Unable to restart secured wifi!");
    		return;
    	}
    }
    
	public void displayToastMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	} 
	
	private static class EfficientAdapter extends BaseAdapter {
       
    	private LayoutInflater inflater;
        private Bitmap iconConnected;
        private Bitmap iconDisconnected;
        
        public EfficientAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
        	inflater = LayoutInflater.from(context);

            // Icons bound to the rows.
            iconConnected = BitmapFactory.decodeResource(context.getResources(), R.drawable.connected);
            iconDisconnected = BitmapFactory.decodeResource(context.getResources(), R.drawable.disconnected);
        }

        /**
         * The number of items in the list is determined by the number of speeches
         * in our array.
         *
         * @see android.widget.ListAdapter#getCount()
         */
        public int getCount() {
        	return clientDataList.size();
        }

        /**
         * Since the data comes from an array, just returning the index is
         * sufficent to get at the data. If we were using a more complex data
         * structure, we would return whatever object represents one row in the
         * list.
         *
         * @see android.widget.ListAdapter#getItem(int)
         */
        public Object getItem(int position) {
        	return clientDataList.get(position);
        }

        /**
         * Use the array index as a unique id.
         *
         * @see android.widget.ListAdapter#getItemId(int)
         */
        public long getItemId(int position) {
            return position;
        }

        public void update() {
        	this.notifyDataSetChanged();
        }
        
        /**
         * Make a view to hold each row.
         *
         * @see android.widget.ListAdapter#getView(int, android.view.View,
         *      android.view.ViewGroup)
         */
        public View getView(int position, View convertView, ViewGroup parent) {
            // A ViewHolder keeps references to children views to avoid unneccessary calls
            // to findViewById() on each row.
            final ViewHolder holder;

            // When convertView is not null, we can reuse it directly, there is no need
            // to reinflate it. We only inflate a new View when the convertView supplied
            // by ListView is null.
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.setuprow, null);

                // Creates a ViewHolder and store references to the two children views
                // we want to bind data to.
                holder = new ViewHolder();
                holder.position = position;
                holder.macAddress = (TextView) convertView.findViewById(R.id.macaddress);
                holder.ipAddress = (TextView) convertView.findViewById(R.id.ipaddress);
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.clientName = (TextView) convertView.findViewById(R.id.clientname);
                
                holder.checkBoxAllowed = (CheckBox) convertView.findViewById(R.id.checkBoxAllowed);
                holder.checkBoxAllowed.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
						ClientData clientData = clientDataList.get(holder.position);
						clientData.setAccessAllowed(isChecked);
						clientDataList.set(holder.position, clientData);
					}
                });

                convertView.setTag(holder);
            } else {
                // Get the ViewHolder back to get fast access to the TextView
                // and the ImageView.
                holder = (ViewHolder) convertView.getTag();
            }

            // Bind the data efficiently with the holder.
            ClientData clientData = clientDataList.get(position);
            
            Log.d("*** DEBUG ***", clientData.getMacAddress()+" -- "+clientData.getClientName()+" -- "+clientData.getIpAddress());
            
            
            holder.macAddress.setText(clientData.getMacAddress());
            if (clientData.isConnected()) {
            	holder.icon.setImageBitmap(this.iconConnected);
            	if (clientData.getIpAddress() != null) {
            		holder.ipAddress.setText(clientData.getIpAddress());
            	}
            	if (clientData.getClientName() != null) {
            		holder.clientName.setText(clientData.getClientName());
            	}
            }
            else {
            	holder.icon.setImageBitmap(this.iconDisconnected);
            	holder.clientName.setText("- Unknown -");
        		holder.ipAddress.setText("- Not connected -");
            }
            if (clientData.isAccessAllowed()) {
            	holder.checkBoxAllowed.setChecked(true);

            }
            return convertView;
        }

        static class ViewHolder {
        	int position;
            TextView macAddress;
            TextView clientName;
            TextView ipAddress;
            ImageView icon;
            CheckBox checkBoxAllowed;
        }
    }
}