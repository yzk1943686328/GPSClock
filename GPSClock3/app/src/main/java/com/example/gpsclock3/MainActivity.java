package com.example.gpsclock3;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;


import java.io.IOException;
import java.lang.String;
import java.util.Timer;
import java.util.TimerTask;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    //动态申请定位权限


    //申请权限成功时
    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void ApplySuccess(){
        initMap();
    }

    //申请权限告诉用户原因时
    @OnShowRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    void showRationaleForMap(PermissionRequest request) {
        showRationaleDialog("使用此功能需要打开定位的权限", request);
    }

    //申请权限被拒绝时
    @OnPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    void onMapDenied() {
        Toast.makeText(this,"你拒绝了权限，该功能不可用",Toast.LENGTH_LONG).show();
    }

    //申请权限被拒绝并勾选不再提醒时
    @OnNeverAskAgain(Manifest.permission.ACCESS_FINE_LOCATION)
    void onMapNeverAskAgain() {
        AskForPermission();
    }

    //告知用户具体需要权限的原因
    private void showRationaleDialog(String messageResId, final PermissionRequest request) {
        new AlertDialog.Builder(this)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull DialogInterface dialog, int which) {
                        request.proceed();//请求权限
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull DialogInterface dialog, int which) {
                        request.cancel();
                    }
                })
                .setCancelable(false)
                .setMessage(messageResId)
                .show();
    }

    //被拒绝并且不再提醒,提示用户去设置界面重新打开权限
    private void AskForPermission() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("当前应用缺少定位权限,请去设置界面打开\n打开之后按两次返回键可回到该应用哦");
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                return;
            }
        });
        builder.setPositiveButton("设置", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + MainActivity.this.getPackageName())); // 根据包名打开对应的设置界面
                startActivity(intent);
            }
        });
        builder.create().show();
    }



    MapView mapview=null;
    BaiduMap baiduMap=null;

    //防止每次定位都重新设置中心点和marker
    private boolean isFirstLocation = true;
    //初始化LocationClient定位类
    private LocationClient mLocationClient = null;
    //BDAbstractLocationListener为7.2版本新增的Abstract类型的监听接口，原有BDLocationListener接口
    private BDAbstractLocationListener myListener = new MyLocationListener();


    //当前经纬度
    private double CurrentLat;
    private double CurrentLon;

    //目的地经纬度
    private double DestinationLat;
    private double DestinationLon;
    LatLng Destination;
    String DestinationName;//目的地名称

    //选择点经纬度以及是否已经选择
    String SelectedDestinationName;
    LatLng SelectedDestination;
    boolean isSelected=false;

    //是否已到达目的地,初始为false
    boolean isArrived=false;
    double Earth_Radius=6371.393;

    //用于开辟新线程检测是否已到达目的地
    Timer timer;
    Handler handle;
    Runnable runnable;



    public void initView(){
        mapview=findViewById(R.id.mv1);
        mapview.removeViewAt(1);
    }

    public void initMap(){

        baiduMap = mapview.getMap();

        //设置地图类型
        baiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        //开启交通图
        baiduMap.setTrafficEnabled(true);
        // 开启定位图层
        baiduMap.setMyLocationEnabled(true);

        }

    public void initLocation(){
         //声明LocationClient类
         mLocationClient = new LocationClient(super.getApplicationContext());
         initLocationOption();

         //注册LocationListener监听器
         MyLocationListener myLocationListener = new MyLocationListener();
         mLocationClient.registerLocationListener(myLocationListener);
         //开始定位
         mLocationClient.start();
     }

     //配置定位参数
    private void initLocationOption() {
        LocationClientOption option = new LocationClientOption();
        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //可选，默认gcj02，设置返回的定位结果坐标系
        option.setCoorType("bd09ll");
        //可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的
        int span = 1500;
        option.setScanSpan(span);//每1.5秒定位一次
        //可选，设置是否需要地址信息，默认不需要
        option.setIsNeedAddress(true);
        //可选，默认false,设置是否使用gps
        option.setOpenGps(true);
        //可选，默认false，设置是否当GPS有效时按照1S/1次频率输出GPS结果
        option.setLocationNotify(true);
        //可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        option.setIsNeedLocationDescribe(true);
        //可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        option.setIsNeedLocationPoiList(true);
        //可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        option.setIgnoreKillProcess(false);
        //可选，默认false，设置是否收集CRASH信息，默认收集
        option.SetIgnoreCacheException(false);
        //可选，默认false，设置是否需要过滤GPS仿真结果，默认需要
        option.setEnableSimulateGps(false);

        option.setOpenAutoNotifyMode();


        //设置locationClientOption
        mLocationClient.setLocOption(option);

    }

    public class MyLocationListener extends BDAbstractLocationListener {

        @Override
        public void onReceiveLocation(BDLocation location) {
            //获取定位结果
            location.getTime();    //获取定位时间
            location.getLocationID();    //获取定位唯一ID，v7.2版本新增，用于排查定位问题
            location.getLocType();    //获取定位类型
            location.getLatitude();    //获取纬度信息
            location.getLongitude();    //获取经度信息
            location.getRadius();    //获取定位精准度
            location.getAddrStr();    //获取地址信息
            location.getCountry();    //获取国家信息
            location.getCountryCode();    //获取国家码
            location.getCity();    //获取城市信息
            location.getCityCode();    //获取城市码
            location.getDistrict();    //获取区县信息
            location.getStreet();    //获取街道信息
            location.getStreetNumber();    //获取街道码
            location.getLocationDescribe();    //获取当前位置描述信息
            location.getPoiList();    //获取当前位置周边POI信息

            location.getBuildingID();    //室内精准定位下，获取楼宇ID
            location.getBuildingName();    //室内精准定位下，获取楼宇名称
            location.getFloor();    //室内精准定位下，获取当前位置所处的楼层信息
            //获取当前经纬度
            CurrentLat = location.getLatitude();
            CurrentLon = location.getLongitude();

            MarkCurrentLocation(baiduMap,location);//标记当前位置

            //这个判断是为了防止每次定位都重新设置中心点
            if (isFirstLocation) {
                setPosition2Center(baiduMap, location, true); //设置并显示中心点
                isFirstLocation = false;
            }

        }
    }

    //标记当前地点
    public void MarkCurrentLocation(BaiduMap map,BDLocation bdLocation){
        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(bdLocation.getRadius())
                .direction(bdLocation.getRadius()).latitude(bdLocation.getLatitude())
                .longitude(bdLocation.getLongitude()).build();
        map.setMyLocationData(locData);
    }

    //将当前位置移至地图中心并缩放
    public void setPosition2Center(BaiduMap map, BDLocation bdLocation, Boolean isShowLoc) {
        if (isShowLoc) {
            LatLng ll = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
            MapStatus.Builder builder = new MapStatus.Builder();
            builder.target(ll).zoom(18f);
            map.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
        }
    }

    public void initDestination(){
        isArrived=false;
        isSelected=true;

        DestinationName=SelectedDestinationName;
        Destination=SelectedDestination;

        DestinationLat=Destination.latitude;
        DestinationLon=Destination.longitude;

        //将该点在地图上标记
        baiduMap.clear();
        BitmapDescriptor bitmap=BitmapDescriptorFactory.fromView(findViewById(R.id.destination));
        MarkerOptions options = new MarkerOptions().position(Destination).icon(bitmap);
        baiduMap.addOverlay(options);

        Toast.makeText(getApplicationContext(),"您已选择"+DestinationName+"为目的地，服务开始",Toast.LENGTH_SHORT).show();
    }
    //点击地图上的点实现目的地选择选择
    public void SetDestination(){
        baiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            boolean isSelected;
            public void onMapClick(LatLng latLng) {

            }

            public void onMapPoiClick(MapPoi mapPoi) {

                SelectedDestinationName=mapPoi.getName();
                SelectedDestination=mapPoi.getPosition();

                //提示用户是否将该点确认为目的地
                AlertDialog.Builder adb=new AlertDialog.Builder(MainActivity.this);
                adb.setTitle("选择目的地");
                if(isSelected==false) {
                    adb.setMessage("您确定要将" + SelectedDestinationName + "设置为目的地吗?");
                }else{
                    adb.setMessage("您确定要将目的地改为"+SelectedDestinationName+"吗？");
                }
                adb.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        initDestination();
                        StartService();
                    }
                });
                adb.setNegativeButton("取消",null);
                adb.show();
            }
        });

    }

    public  void StartService(){
        handle=new Handler();
        runnable=new Runnable() {
            @Override
            public void run() {


                if(isArrived){
                    Toast.makeText(getApplicationContext(), DestinationName+"已到达",Toast.LENGTH_SHORT).show();
                    isSelected=false;
                    isArrived=false;
                    try {
                        PlayMusic();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }else{

                    if(distance(CurrentLat,CurrentLon,DestinationLat,DestinationLon)<100){
                        isArrived=true;
                    }
                    handle.postDelayed(runnable,1000);
                }


            }
        };

        handle.post(runnable);
    }

    //到达终点播放音乐
    public void PlayMusic() throws IOException {
        AssetFileDescriptor fd = getAssets().openFd("NotifyMusic.mp3");
        MediaPlayer mediaPlayer=new MediaPlayer();
        mediaPlayer.setDataSource(fd.getFileDescriptor());
        mediaPlayer.prepare();
        mediaPlayer.start();
    }

    //计算当前位置和目的地的距离
    public double distance(double lat1, double lon1, double lat2, double lon2) {

        //将经纬度转化为弧度制
        lat1=rad(lat1);
        lat2=rad(lat2);
        lon1=rad(lon1);
        lon2=rad(lon2);

        //设所求点A ，纬度角β1 ，经度角α1 ；点B ，纬度角β2 ，经度角α2。
        // 则距离S=R·arc cos[cosβ1cosβ2cos（α1-α2）+sinβ1sinβ2]，其中R为球体半径。
        double s=1000*Earth_Radius*Math.acos(Math.cos(lat1)*Math.cos(lat2)*Math.cos(lon1-lon2)+Math.sin(lat1)*Math.sin(lat2));

        return  s;
    }

    //角度值转弧度值
    public double rad(double d) {
        return d * Math.PI / 180;
    }


    public void Alert(){
        AlertDialog.Builder alert=new AlertDialog.Builder(MainActivity.this);
        alert.setTitle("提醒");
        alert.setMessage("如果您想选择目的地，请点击地图上的地点");
        alert.setPositiveButton("我知道了",null);
        alert.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);

        initView();

        //当android系统小于5.0的时候直接定位显示，不用动态申请权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            initMap();
        } else {
            //MainActivityPermissionsDispatcher.ApplySuccessWithCheck(this);
        }

        initLocation();
        SetDestination();

        Alert();

    }

    public void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mapview.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mapview.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        // 关闭定位图层
        baiduMap.setMyLocationEnabled(false);
        mapview.onDestroy();
        mapview = null;
    }

}