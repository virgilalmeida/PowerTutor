/*
Copyright (C) 2011 The University of Michigan

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Please send inquiries to powertutor@umich.edu
*/

package edu.umich.PowerTutor.components;

import edu.umich.PowerTutor.PowerNotifications;
import edu.umich.PowerTutor.service.IterationData;
import edu.umich.PowerTutor.service.PowerData;
import edu.umich.PowerTutor.util.NotificationService;
import edu.umich.PowerTutor.util.Recycler;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.TreeSet;

/**This class aims to log the audio device status once per log interval*/
public class GPSWifi extends PowerComponent {
  /**This class is the logger data file corresponding to Audio*/
  public static class GPSWifiData extends PowerData {
    private static Recycler<GPSWifiData> recycler = new Recycler<GPSWifiData>();
    
    public static GPSWifiData obtain() {
    	GPSWifiData result = recycler.obtain();
      if(result != null) return result;
      return new GPSWifiData();
    }

    @Override
    public void recycle() {
      recycler.recycle(this);
    }

    public int time;
    public List<ScanResult> aps;
    public String gpsMessage;
    public String mobileSubType;
    public String singalStenths;
  
    private GPSWifiData() {
    }

    public void init(int time,String gpsMessage, String mobileSubType, String signalStrnths, List<ScanResult> aps) {
    	this.time = time;
    	this.aps = aps;
    	this.gpsMessage= gpsMessage;
    	this.mobileSubType = mobileSubType;
    	this.singalStenths = signalStrnths;
    }
  
    public void writeLogDataInfo(OutputStreamWriter out) throws IOException {
    	String data = time + "#" + "0.0" +"#"+gpsMessage + "#"+ mobileSubType+ "#"+ singalStenths+ "#"+ 
			aps.size();
			for (ScanResult s : aps)
				data += "#"+s.SSID.replaceAll("\\s", "") + "#"+ s.BSSID + "#"  + s.level;
			data += "\n";
      out.write(data);
    }
  }
  private static class MediaData implements Comparable {
	    private static Recycler<MediaData> recycler = new Recycler<MediaData>();
	    
	    public static MediaData obtain() {
	      MediaData result = recycler.obtain();
	      if(result != null) return result;
	      return new MediaData();
	    }

	    public void recycle() {
	      recycler.recycle(this);
	    }

	    public int uid;
	    public int id;
	    public int assignUid;

	    public int compareTo(Object obj) {
	      MediaData x = (MediaData)obj;
	      if(uid < x.uid) return -1;
	      if(uid > x.uid) return 1;
	      if(id < x.id) return -1;
	      if(id > x.id) return 1;
	      return 0;
	    }

	    public boolean equals(Object obj) {
	      MediaData x = (MediaData)obj;
	      return uid == x.uid && id == x.id;
	    }
	  }

  private PowerNotifications GPSWifiNotif;
  private TreeSet<MediaData> uidData;
  
  private LocationManager locationManager;
  private String gpsMessage = "0.0#0.0#0.0";
  private WifiManager wifiManager;
  private NetworkInfo mobile;
  private String mobileSubType = "unkown";
  private ConnectivityManager connectManager;
  private int singalStenths =0; 

  public GPSWifi(Context context) {
    if(NotificationService.available()) {
      uidData = new TreeSet<MediaData>();
      GPSWifiNotif = new NotificationService.DefaultReceiver() {
        private int sysUid = -1;

        @Override
        public void noteSystemMediaCall(int uid) {
          sysUid = uid;
        }

        @Override
        public void noteStartMedia(int uid, int id) {
          MediaData data = MediaData.obtain();
          data.uid = uid;
          data.id = id;
          if(uid == 1000 && sysUid != -1) {
            data.assignUid = sysUid;
            sysUid = -1;
          } else {
            data.assignUid = uid;
          }
          synchronized(uidData) {
            if(!uidData.add(data)) {
              data.recycle();
            }
          }
        }

        @Override
        public void noteStopMedia(int uid, int id) {
          MediaData data = MediaData.obtain();
          data.uid = uid;
          data.id = id;
          synchronized(uidData) {
            uidData.remove(data);
          }
          data.recycle();
        }
      };
      NotificationService.addHook(GPSWifiNotif);
    }

    locationManager = (LocationManager)context.getSystemService(
                                             Context.LOCATION_SERVICE);
    wifiManager = (WifiManager)context.getSystemService(
            Context.WIFI_SERVICE);
    
    connectManager = (ConnectivityManager)context.getSystemService(
            Context.CONNECTIVITY_SERVICE);
   
  }

  @Override
  protected void onExit() {
    if(GPSWifiNotif != null) {
      NotificationService.removeHook(GPSWifiNotif);
    }
  }

  @Override
  public IterationData calculateIteration(long iteration) {
    IterationData result = IterationData.obtain();
    GPSWifiData data = GPSWifiData.obtain();
    
    
    String gpsMessageIteration="0.0#0.0#0.0";
    Location m = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); 
  //Use last location time inside two minutes error
  		if (m!= null && (m.getTime() - System.currentTimeMillis() < (1000 * 60 * 2)))//TWO MINUTES
  			gpsMessageIteration = m.getLongitude()+"#"+m.getLatitude()+"#"+m.getSpeed();
  		
  		wifiManager.startScan();
  		
  		List<ScanResult> apsIteration = wifiManager.getScanResults();
  		
    data.init((int)iteration,gpsMessageIteration,"unkown","0",apsIteration);
    result.setPowerData(data);

    if(uidData != null) synchronized(uidData) {
      int last_uid = -1;
      for(MediaData dat : uidData) {
        if(dat.uid != last_uid) {
          GPSWifiData audioPower = GPSWifiData.obtain();
          audioPower.init((int)iteration,gpsMessageIteration,"unkown","0",apsIteration);
          result.addUidPowerData(dat.assignUid, audioPower);
        }
        last_uid = dat.uid;
      }
    }

    return result;
  }

  @Override
  public boolean hasUidInformation() {
    return true;
  }

  @Override
  public String getComponentName() {
    return "GPSWifi";
  }
}
