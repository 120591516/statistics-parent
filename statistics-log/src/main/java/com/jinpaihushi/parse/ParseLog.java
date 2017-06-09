package com.jinpaihushi.parse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import com.jinpaihushi.model.Accesslog;
import com.jinpaihushi.model.AccesslogSpread;
import com.jinpaihushi.model.Product;
import com.jinpaihushi.model.ProductExample;
import com.jinpaihushi.model.ProductExample.Criteria;
import com.jinpaihushi.util.MyPredicate;

public class ParseLog {

    private static SqlSessionFactory factory;
    static {
        try {
            Reader reader = Resources.getResourceAsReader("SqlMapConfig.xml");
            factory = new SqlSessionFactoryBuilder().build(reader);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String baseUrlPrefix = "/ComeIn?m=setOneProductNew&";// 微信服务号平台

    private static String wxNurse114UrlPrefix = "/wxNurse114";// 微信114生活助手

    private static SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd");

    private static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * 读取文件信息
     * 
     * @param fileName
     *            文件路径D:\Program
     *            Files\eclipse\workspace\ParseLogTest\src\access_20170604.log
     * @throws Exception
     */
    public static void readFileByLines() {
        //读取文件每次读取文件的1/10
        // 先获取未解析日志时间
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        Date time = cal.getTime();
        String yesterday = dayFormat.format(time);
        // 文件路径
        // String filePath = "";
        String fileName = "D:/Program Files/eclipse/workspace/br-pro-sqlserver/src/main/java/access_20170604.log";
        File file = new File(fileName);
        BufferedReader reader = null;
        try {
            //定义文件的第一次读取到的大小
            RandomAccessFile randomFile = new RandomAccessFile(file, "r");
            randomFile.seek(file.length() / 10);
            String tmp = null;
            while ((tmp = randomFile.readLine()) != null) {
                System.out.println(tmp);
            }
            // 以行为单位读取文件内容，一次读一整行
            InputStreamReader isr = new InputStreamReader(new FileInputStream(file), "UTF-8");
            reader = new BufferedReader(isr);
            String tempString = null;
            // 筛选出的数据
            List<AccesslogSpread> wxList = new ArrayList<AccesslogSpread>();
            List<AccesslogSpread> wxNurse114List = new ArrayList<AccesslogSpread>();
            AccesslogSpread al = null;
            // 一次读入一行，直到读入null为文件结束
            while ((tempString = reader.readLine()) != null) {
                // 获取访问时间的小时数
                // 获取当前访问时间的时分秒
                Stream<String> lines = reader.lines();
                if (tempString.contains(baseUrlPrefix)) {
                    al = new AccesslogSpread();
                    int timeIndex = tempString.indexOf(":");
                    String hourse = tempString.substring(timeIndex + 1, timeIndex + 3);
                    String startTime = hourse + ":00:00";
                    String endTime = hourse + ":59:59";
                    // 获取ip地址 根据ip判断pv、uv
                    int ipindex = tempString.indexOf("-");
                    String ipaddress = tempString.substring(0, ipindex - 1);
                    // 获取产品地址
                    int urlStart = tempString.indexOf(baseUrlPrefix);
                    int urlEnd = tempString.indexOf("HTTP");
                    String urladdress = tempString.substring(urlStart, urlEnd);
                    //访问的商品的id有两位、三位，统一按三位截取，然后两位的去前后空格
                    urladdress = urladdress.trim();
                    al.setAccesstime(dayFormat.parse(yesterday));
                    al.setIp(ipaddress);
                    al.setStarttime(timeFormat.parse(startTime));
                    al.setEndtime(timeFormat.parse(endTime));
                    al.setProductPath(urladdress);
                    if (tempString.contains(wxNurse114UrlPrefix)) {
                        al.setPlatformId(2);
                        wxNurse114List.add(al);
                    }
                    else {
                        al.setPlatformId(1);
                        wxList.add(al);

                    }
                }
            }
            reader.close();
            extracted(wxList, yesterday);
            extracted(wxNurse114List, yesterday);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e1) {
                }
            }
        }
    }

    private static void extracted(List<AccesslogSpread> list, String dayTime)
            throws ParseException, IllegalAccessException, InvocationTargetException {
        List<Accesslog> logList = new ArrayList<>();
        SqlSession openSession = factory.openSession();
        List<AccesslogSpread> allProduct = new ArrayList<>(list);
        // 获取所有访问商品列表
        for (int i = 0; i < allProduct.size(); i++) {
            for (int j = allProduct.size() - 1; j > i; j--) {
                if (allProduct.get(i).getProductPath().equals(allProduct.get(j).getProductPath())) {
                    allProduct.remove(j);
                }
            }
        }
        Accesslog accesslog = null;
        Accesslog sourceTime = null;
        for (AccesslogSpread accesslog2 : allProduct) {
            // 根据商品的地址获取商品一天内的访问次数
            Predicate predicate = new MyPredicate("productPath", accesslog2.getProductPath());
            List<AccesslogSpread> select = (List<AccesslogSpread>) CollectionUtils.select(list, predicate);
            ProductExample productExample = new ProductExample();
            Criteria productCriteria = productExample.createCriteria();
            productCriteria.andPathEqualTo(accesslog2.getProductPath());
            List<Product> product = openSession.selectList("com.jinpaihushi.mapper.ProductMapper.selectByExample",
                    productExample);
            if (product.size() > 0) {
                System.out.println(product.get(0).getId());
                // 获取某一商品的各个时间段
                List<AccesslogSpread> timeList = new ArrayList<>(select);
                for (int i = 0; i < timeList.size(); i++) {
                    for (int j = timeList.size() - 1; j > i; j--) {
                        if (timeList.get(i).getStarttime().equals(timeList.get(j).getStarttime())) {
                            timeList.remove(j);
                        }
                    }
                }
                for (AccesslogSpread accesslog3 : timeList) {
                    sourceTime = new Accesslog();
                    BeanUtils.copyProperties(sourceTime, accesslog3);
                    sourceTime.setProductId(product.get(0).getId());
                    Predicate pvPredicate = new MyPredicate("starttime", accesslog3.getStarttime());
                    List<AccesslogSpread> pv = (List<AccesslogSpread>) CollectionUtils.select(select, pvPredicate);
                    sourceTime.setPv(pv.size());
                    // 如果时间段的日志数==1说明该时间段有且只有一个ip访问pv、uv值相等
                    // 反之就进行判断
                    if (pv.size() > 1) {
                        for (int i = 0; i < pv.size() - 1; i++) {
                            for (int j = 1; j < pv.size(); j++) {
                                if (pv.get(i).getIp().equals(pv.get(j).getIp())) {
                                    pv.remove(j);
                                }
                            }
                        }
                    }
                    sourceTime.setUv(pv.size());
                    logList.add(sourceTime);
                }
                accesslog = new Accesslog();
                BeanUtils.copyProperties(accesslog, accesslog2);
                accesslog.setProductId(product.get(0).getId());
                accesslog.setStarttime(timeFormat.parse("00:00:00"));
                accesslog.setEndtime(timeFormat.parse("23:59:59"));
                accesslog.setPv(select.size());
                for (int i = 0; i < select.size(); i++) {
                    for (int j = select.size() - 1; j > i; j--) {
                        if (select.get(i).getIp().equals(select.get(j).getIp())) {
                            select.remove(j);
                        }
                    }
                }
                accesslog.setUv(select.size());
                logList.add(accesslog);
            }
        }
        for (Accesslog accesslog1 : logList) {
            // 将数据插入到数据库
            openSession.insert("com.jinpaihushi.mapper.AccesslogMapper.insert", accesslog1);
        }
        openSession.commit();
        openSession.close();
    }

    public static void main(String[] args) {
        String fileName = "D:/Program Files/eclipse/workspace/br-pro-sqlserver/src/main/java/access_20170604.log";
        List<Accesslog> logList = new ArrayList<>();
        File file = new File(fileName);
        BufferedReader reader = null;
        try {
            FileReader fileReader = new FileReader(fileName);
            System.out.println("以行为单位读取文件内容，一次读一整行：");
            InputStreamReader isr = new InputStreamReader(new FileInputStream(file), "UTF-8");
            reader = new BufferedReader(isr);
            String tempString = null;
            List<AccesslogSpread> list = new ArrayList<AccesslogSpread>();
            AccesslogSpread al = null;
            String dayTime = fileName.substring(fileName.length() - 12, fileName.length() - 4);
            System.out.println("当前时间：" + dayTime);
            System.out.println(dayFormat.parse(dayTime));

            LineNumberReader l = new LineNumberReader(fileReader);
            System.out.println(l.getLineNumber());
        }
        catch (Exception e) {

        }
    }

    /**
     * @param fileName 读取的文件
     * @param index 开始位置
     * @param num 读取量
     * @return
     */
    public List<AccesslogSpread> readLine(String fileName, int index, int num) {

        String dayTime = fileName.substring(fileName.length() - 12, fileName.length() - 4);
        List<AccesslogSpread> list = new ArrayList<>();
        LineNumberReader reader = null;
        AccesslogSpread al = null;
        try {
            FileReader fileReader = new FileReader(fileName);
            reader = new LineNumberReader(fileReader);
            if (index > 0) {
                reader.skip(index);
            }
            while (true) {
                String tempString = reader.readLine();
                if (StringUtils.isNotEmpty(tempString)) {
                    // 获取访问时间的小时数
                    // 获取当前访问时间的时分秒
                    if (tempString.contains(baseUrlPrefix)) {
                        al = new AccesslogSpread();
                        int timeIndex = tempString.indexOf(":");
                        String hourse = tempString.substring(timeIndex + 1, timeIndex + 3);
                        String startTime = hourse + ":00:00";
                        String endTime = hourse + ":59:59";
                        // 获取ip地址 根据ip判断pv、uv
                        int ipindex = tempString.indexOf("-");
                        String ipaddress = tempString.substring(0, ipindex - 1);
                        // 获取产品地址
                        int urlStart = tempString.indexOf(baseUrlPrefix);
                        int urlEnd = tempString.indexOf("HTTP");
                        String urladdress = tempString.substring(urlStart, urlEnd);
                        //访问的商品的id有两位、三位，统一按三位截取，然后两位的去前后空格
                        urladdress = urladdress.trim();
                        al.setAccesstime(dayFormat.parse(dayTime));
                        al.setIp(ipaddress);
                        al.setStarttime(timeFormat.parse(startTime));
                        al.setEndtime(timeFormat.parse(endTime));
                        al.setProductPath(urladdress);
                        if (tempString.contains(wxNurse114UrlPrefix)) {
                            al.setPlatformId(2);
                        }
                        else {
                            al.setPlatformId(1);

                        }
                        list.add(al);
                    }
                }
                if (num == list.size()) {
                    break;
                }
            }
            reader.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
