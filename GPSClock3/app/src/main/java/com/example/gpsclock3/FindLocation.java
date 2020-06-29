package com.example.gpsclock3;

import com.baidu.location.LocationClient;

public class FindLocation implements Runnable {
    LocationClient mLocationClient=null;

    public FindLocation(LocationClient mLocationClient){
        this.mLocationClient=mLocationClient;
    }


    public void run() {
        while(true){
            try {
                Thread.sleep(200);
                mLocationClient.start();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
