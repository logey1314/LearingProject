package com.itheima.service;

import com.itheima.exception.BusinessException;
import com.itheima.util.AgreementUtil;
import com.itheima.util.IOUtil;

import java.io.*;
import java.net.Socket;
import java.util.ResourceBundle;

/*
    协议定义:   协议+数据
    第一行是协议，第二行开始就是数据
 */
public class FileUpDownServiceImp implements Runnable, FileUpDownService {

    private final ResourceBundle bundle;
    private final File rootDir;
    private Socket socket;

    public FileUpDownServiceImp(Socket socket) {
        this.socket = socket;
        //1 读取配置文件中的端口，根目录等配置信息
        bundle = ResourceBundle.getBundle("yunpan");
        //根目录  rootDir = D:\\img
        rootDir = new File(bundle.getString("rootDir"));
        if (rootDir.isFile()) {
            throw new BusinessException("根目录路径与已存在文件冲突");
        } else if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw new BusinessException("根目录创建失败，请检查配置路径是否正确");
        }
    }

    @Override
    public void run() {
        try (Socket socket = this.socket;
             InputStream netIn = socket.getInputStream();
             OutputStream netOut = socket.getOutputStream();
        ) {
            // 读协议
            final String agreement = AgreementUtil.receiveAgreement(netIn);
            //Type=scan，FileName=root，Status=null,Message=null
            // System.out.println("接收客户端数据：" + agreement);

            // 解析字符串
            String type = AgreementUtil.getType(agreement);
            //scan
            // System.out.println("解析字符串的数据类型:" + type);
            switch (type) {
                case "SCAN"://客户端要浏览
                    scanDirectory(agreement, netIn, netOut);//scan
                    break;
                case "DOWNLOAD"://客户端要下载
                    downloadFile(agreement, netIn, netOut);
                    break;
                case "UPLOAD"://客户端要上传
                    uploadFile(agreement, netIn, netOut);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 浏览目录
    @Override
    public void scanDirectory(String agreement, InputStream netIn, OutputStream netOut) throws IOException {
        //响应客户端使用
        //获取客户端想要浏览的目录
        String fileName = AgreementUtil.getFileName(agreement);// chid
        //root是提供给客户端的虚拟路径，转换为服务端的真实路径



        String fileDir = fileName.replace("root", rootDir.toString());
        File dir = new File(fileDir);





        if (dir.isFile()) {
            // 封装协议
            String s = AgreementUtil.getAgreement("SCAN", null, "FAILED", "目录不存在.只能浏览当前子目录");
            // 发送协议
            AgreementUtil.sendAgreement(netOut, s);
        } else {
            // 封装协议  123
            String s = AgreementUtil.getAgreement("SCAN", fileDir, "OK", null);
            //scan 路径 ok null   /r数据
            AgreementUtil.sendAgreement(netOut, s);

            //把具体数据随后发送
            //把文件数据按照："文件类型 名称"   发送，每一个子文件一行
            OutputStreamWriter osw = new OutputStreamWriter(netOut);
            File[] children = dir.listFiles();

            for (File child : children) {
                String fileType = child.isFile() ? "文件" : "目录";
                osw.write(fileType + " " + child.getName() + "\r\n");//每个文件一行
            }
            //刷新数据
            osw.flush();
        }
    }

    // 文件上传功能
    @Override
    public void uploadFile(String agreement, InputStream netIn, OutputStream netOut) throws IOException {
        File upDir = new File(bundle.getString("clientUpDir"));
        String filedir = AgreementUtil.getFileName(agreement);
        String fileName = filedir.substring(filedir.lastIndexOf("\\") + 1);
        File newFile = new File(upDir, fileName);
        System.out.println("--------"+"开始接收"+"----------------");
        try(FileOutputStream fos = new FileOutputStream(newFile);
        BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = netIn.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            bos.flush();
            System.out.println("--------"+"接收成功"+"----------------");
            String up = AgreementUtil.getAgreement("UPLOAD", fileName, "OK", "上传成功");
            AgreementUtil.sendAgreement(netOut, up);
        } catch (Exception e) {
            System.out.println("文件接收过程中发生异常: " + e.getMessage());
            e.printStackTrace();

            String up = AgreementUtil.getAgreement("UPLOAD", fileName, "ERROR", "上传失败: " + e.getMessage());
            System.out.println("发送失败响应: " + up);
            AgreementUtil.sendAgreement(netOut, up);
        }

    }


/*    // 服务端上传处理代码修改
    @Override
    public void uploadFile(String agreement, InputStream netIn, OutputStream netOut) throws IOException {
        System.out.println("收到上传请求: " + agreement);

        File upDir = new File(bundle.getString("clientUpDir"));
        if (!upDir.exists()) {
            upDir.mkdirs();
            System.out.println("创建上传目录: " + upDir.getAbsolutePath());
        }

        String filedir = AgreementUtil.getFileName(agreement);
        String fileName = filedir.substring(filedir.lastIndexOf("\\") + 1);
        File newFile = new File(upDir, fileName);

        System.out.println("准备接收文件: " + newFile.getAbsolutePath());

        try (FileOutputStream fos = new FileOutputStream(newFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            byte[] buf = new byte[4096];
            int len;
            long totalReceived = 0;

            System.out.println("开始接收文件数据...");

            while ((len = netIn.read(buf)) != -1) {
                bos.write(buf, 0, len);
                totalReceived += len;
                if (totalReceived % (1024 * 1024) == 0) { // 每MB打印一次
                    System.out.println("已接收: " + (totalReceived / 1024 / 1024) + "MB");
                }
            }

            bos.flush();
            System.out.println("文件接收完毕，总共接收: " + totalReceived + " 字节");
            System.out.println("保存的文件大小: " + newFile.length() + " 字节");

            String up = AgreementUtil.getAgreement("UPLOAD", fileName, "OK", "上传成功");
            System.out.println("发送成功响应: " + up);
            AgreementUtil.sendAgreement(netOut, up);
        } catch (Exception e) {
            System.out.println("文件接收过程中发生异常: " + e.getMessage());
            e.printStackTrace();

            String up = AgreementUtil.getAgreement("UPLOAD", fileName, "ERROR", "上传失败: " + e.getMessage());
            System.out.println("发送失败响应: " + up);
            AgreementUtil.sendAgreement(netOut, up);
        }
    }*/



    // 文件下载功能
    @Override
    public void downloadFile(String agreement, InputStream netIn, OutputStream netOut) throws IOException {

        //响应客户端使用
        //获取客户端想要浏览的目录
        String fileName = AgreementUtil.getFileName(agreement);// chid
        //root是提供给客户端的虚拟路径，转换为服务端的真实路径

        //String fileDir = fileName.replace("root", rootDir.toString());
        String fileDir=fileName;
        File dir = new File(fileDir);

        if (dir.exists()&&!dir.isDirectory()) {
            /*// 封装协议
            String s = AgreementUtil.getAgreement("DOWNLOAD", null, "FAILED", "目录不存在.只能浏览当前子目录");
            // 发送协议
            AgreementUtil.sendAgreement(netOut, s);*/
            String s = AgreementUtil.getAgreement("DOWNLOAD", dir.getName(), "OK", "null");
            AgreementUtil.sendAgreement(netOut, s);
            try(FileInputStream fileInputStream=new FileInputStream(dir);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = bufferedInputStream.read(buf)) != -1) {
                    netOut.write(buf, 0, len);
                }
                netOut.flush();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }


        } else {
            String s = AgreementUtil.getAgreement("DOWNLOAD", null, "FAILED", "文件不存在或为目录");
            AgreementUtil.sendAgreement(netOut, s);
        }



    }
}