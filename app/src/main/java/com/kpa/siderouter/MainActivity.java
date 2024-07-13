package com.kpa.siderouter;

import android.graphics.Color;
import android.net.InetAddresses;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Button myButton = findViewById(R.id.btn_run);
        myButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runSideRouter();
            }
        });
        Button btnClose = findViewById(R.id.btn_stop);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSideRouter();
            }
        });

        updateInfo();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d("test","on Restart");
        updateInfo();
    }



    private void updateInfo(){
        TextView valueRoot = findViewById(R.id.value_root);
        TextView valueNic1 = findViewById(R.id.value_nic1);
        TextView valueNic2 = findViewById(R.id.value_nic2);
        TextView valueStatus = findViewById(R.id.value_status);

        // 动态修改 TextView 的值
        // 修改是否root的值
        valueNic1.setText("eth0");    // 修改网卡1的值
        valueNic2.setText("wlan1");   // 修改网卡2的值
        valueStatus.setText("已断开"); // 修改状态的值

        if(isRoot()){
            valueRoot.setText("开启");
            valueRoot.setTextColor(Color.rgb(0,1,0));
        }else{
            valueRoot.setText("无");
            valueRoot.setTextColor(Color.rgb(0,0,0));
        }
        NetworkInterface net1= checkInterface("tun0");
        if(net1==null){
            valueNic1.setText("未开启");
        }else{
            String ipv4 = getIpv4(net1);
            String ipv6 = getIpv6(net1);
            if(ipv4!=null&&!ipv4.isEmpty()){
                valueNic1.setText(ipv4);
            }else if(ipv6!=null&&!ipv6.isEmpty()){
                valueNic1.setText(ipv6);
            }else{
                valueNic1.setText("not found");
            }
        }

        NetworkInterface net2= checkInterface("wlan0");
        if(net2==null){
            valueNic2.setText("未开启");
        }else{
            String ipv4 = getIpv4(net2);
            String ipv6 = getIpv6(net2);
            if(ipv4!=null&&!ipv4.isEmpty()){
                valueNic2.setText(ipv4);
            }else if(ipv6!=null&&!ipv6.isEmpty()){
                valueNic2.setText(ipv6);
            }else{
                valueNic2.setText("not found");
            }


        }
        String status = getIpv4Forward();
        if(status==null){
            valueStatus.setText("错误");
        }else{
            if(status.contains("0")){
                valueStatus.setText("未开启");
            }else if(status.contains("1")){
                valueStatus.setText("生效中");
            }else{
                valueStatus.setText("未知:"+status);
            }
        }
    }

    private void runSideRouter(){

//        #!/system/bin/sh
//
//        tun='tun0' #虚拟接口名称
//                dev='wlan0' #物理接口名称，eth0、wlan0
//                interval=3 #检测网络状态间隔(秒)
//        pref=18000 #路由策略优先级
//
//        # 开启IP转发功能
//        sysctl -w net.ipv4.ip_forward=1
//
//        # 清除filter表转发链规则
//        iptables -F FORWARD
//
//        # 添加NAT转换，部分第三方VPN需要此设置否则无法上网，若要关闭请注释掉
//        iptables -t nat -A POSTROUTING -o $tun -j MASQUERADE
//
//        # 添加路由策略
//        ip rule add from all table main pref $pref
//        ip rule add from all iif $dev table $tun pref $(expr $pref - 1)
//
//        contain="from all iif $dev lookup $tun"
//
//        while true ;do
//            if [[ $(ip rule) != *$contain* ]]; then
//        if [[ $(ip ad|grep 'state UP') != *$dev* ]]; then
//        echo -e "[$(date "+%H:%M:%S")]dev has been lost."
//            else
//        ip rule add from all iif $dev table $tun pref $(expr $pref - 1)
//        echo -e "[$(date "+%H:%M:%S")]network changed, reset the routing policy."
//        fi
//                fi
//        sleep $interval
//        done
        if(!isRoot()){
            Toast.makeText(MainActivity.this, "需要root权限!", Toast.LENGTH_SHORT).show();
            return;
        }
        NetworkInterface net1= checkInterface("tun0");
        if(net1==null){
            Toast.makeText(MainActivity.this, "请打开VPN!", Toast.LENGTH_SHORT).show();
            return;
        }
        NetworkInterface net2= checkInterface("wlan0");
        if(net2==null){
            Toast.makeText(MainActivity.this, "请打开无线网!", Toast.LENGTH_SHORT).show();
            return;
        }
        //# 开启IP转发功能
        shell(new String[]{"su","-c","sysctl -w net.ipv4.ip_forward=1"});

        //# 清除filter表转发链规则
        shell(new String[]{"su","-c","iptables -F FORWARD"});

        //# 添加NAT转换，部分第三方VPN需要此设置否则无法上网，若要关闭请注释掉
        CheckBox cbNet = findViewById(R.id.value_checkbox);
        if(cbNet.isChecked()){
            shell(new String[]{"su","-c","iptables -t nat -A POSTROUTING -o tun0 -j MASQUERADE"});
        }
        //# 添加路由策略
        shell(new String[]{"su","-c","ip rule add from all table main pref 18000"});
        shell(new String[]{"su","-c","ip rule add from all iif wlan0 table tun0 pref $(expr 18000 - 1)"});


        updateInfo();
        Toast.makeText(MainActivity.this, "开启成功", Toast.LENGTH_SHORT).show();
    }

    private  void stopSideRouter(){
        if(!isRoot()){
            Toast.makeText(MainActivity.this, "需要root权限!", Toast.LENGTH_SHORT).show();
            return;
        }
        //# 开启IP转发功能
        shell(new String[]{"su","-c","sysctl -w net.ipv4.ip_forward=0"});

        //# 清除filter表转发链规则
        shell(new String[]{"su","-c","iptables -F FORWARD"});

        shell(new String[]{"su","-c","ip rule del from all table main pref 18000"});
        shell(new String[]{"su","-c","ip rule del from all iif wlan0 table tun0 pref $(expr 18000 - 1)"});


        Toast.makeText(MainActivity.this, "已关闭!", Toast.LENGTH_SHORT).show();

        updateInfo();
    }

    private boolean shell(String[] cmd){
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(cmd);
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line =in.readLine();
            if ( line!= null) {
                Log.d("shell",line);
                return true;
            }
            return false;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private String getIpv4Forward(){
        //cat /proc/sys/net/ipv4/ip_forward
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] {"cat","/proc/sys/net/ipv4/ip_forward"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line =in.readLine();
            if ( line!= null) {
                return line;
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (process != null) process.destroy();
        }
    }
    private boolean isRoot(){
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] {"/system/bin/which", "su"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) {
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private NetworkInterface checkInterface(String name){
        try {
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()){
                NetworkInterface net = interfaces.nextElement();
                Log.d("Test",net.getName());
                if(net.getName().equals(name)){
                    return net;
                }
            }

        } catch (SocketException e) {
            e.printStackTrace();
        }
        return  null;
    }

    private String getIpv4(NetworkInterface net){
        Enumeration<InetAddress> address= net.getInetAddresses();
        while (address.hasMoreElements()){
            InetAddress add = address.nextElement();
            if(!add.isLoopbackAddress()&&add.getAddress().length==4){
                return  add.getHostAddress();
            }
        }
        return null;
    }
    private String getIpv6(NetworkInterface net){
        Enumeration<InetAddress> address= net.getInetAddresses();
        while (address.hasMoreElements()){
            InetAddress add = address.nextElement();
            if(!add.isLoopbackAddress()&&add.getAddress().length==16){
                return add.getHostAddress();
            }
        }
        return null;
    }
}