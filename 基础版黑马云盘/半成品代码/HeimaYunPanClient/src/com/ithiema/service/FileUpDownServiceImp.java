package com.ithiema.service;

import com.ithiema.exception.BusinessException;
import com.ithiema.util.AgreementUtil;

import java.io.*;
import java.net.Socket;
import java.util.ResourceBundle;
import java.util.Scanner;

/*
    ctrl + shift + "-" : 收缩所有的方法快捷键
    ctrl + shift + "+" : 展开所有的方法快捷键
    ctrl + alt + 左键 : 回退
    ctrl + alt + 右键 : 前进
 */
public class FileUpDownServiceImp implements FileUpDownService {
    /**
     * 用来保存当前浏览的目录
     * 解释 : 其实就是服务器存储数据的文件夹路径
     * 服务器src根目录下的yunpan.properties中的 rootDir
     */
    public File current = new File("root");
    private ResourceBundle bundle;
    private String downloadPath;// 下载的路径

    @Override
    public void start() {
        try {
            // 初始化下载路径，读取配置文件中的数据
            // ResourceBundle读取的是src下的properties文件
            bundle = ResourceBundle.getBundle("yunpan");

            // 根据文件中的键找到对应的值 , 获取对应的值 ---> 就是下载的路径
            // "D:\\download"
            downloadPath = bundle.getString("DownloadPath");

            // 把下载的路径的字符串 , 封装成一个File对象
            File downloadDir = new File(downloadPath);

            // 判断此路径是否是一个文件夹路径
            // 什么时候会抛出异常 ? 此路径是一个文件 , 文件不存在还创建失败了!
            if (downloadDir.isFile()) {
                throw new BusinessException("文件不能当做下载目录，请更改下载路径配置！");
            } else if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                throw new BusinessException("下载目录初始化失败，请检查下载路径配置是否正确！");
            }

            Scanner sc = new Scanner(System.in);
            System.out.println("*******欢迎进入黑马网盘*******");

            while (true) {
                System.out.println("***************************************************************************");
                System.out.println("1)浏览当前目录 \t2)浏览子目录 \t3)返回上一级目录 \t4)下载文件 \t5)上传文件");
                System.out.println("***************************************************************************");
                String choice = sc.nextLine();
                switch (choice) {
                    case "1":
                        scanDirection(current);
                        //System.out.println("___"+current.getName()+"__");
                        break;
                    case "2":
                        System.out.println("请输入要浏览的子目录：");
                        String dir = sc.nextLine();
                        try {
                            //扫描
                            scanDirection(new File(current, dir));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case "3":
                        if (current.getName().equals("root")) {
                            System.out.println("没有上一级了");
                        } else {
                            //System.out.println("___"+current.getParentFile().getName()+"__");
                            scanDirection(current.getParentFile());
                        }
                        break;
                    case "4":
                        //下载
                        System.out.println("请输入要下载的文件名（含后缀）:");
                        String fileName = sc.nextLine();
                        downloadFile(new File(current, fileName));
                        break;
                    case "5":
                        while (true) {
                            System.out.println("请输入在计算机中要上传的文件路径");
                            String uploadFilePath = sc.nextLine();
                            File upFile = new File(uploadFilePath);
                            if (!upFile.exists()) {
                                System.out.println("文件不存在，请重新输入！");
                            } else if (upFile.isDirectory()) {
                                System.out.println("抱歉，不支持目录上传！");
                            } else if (upFile.isFile()) {
                                uploadFile(upFile);
                                break;
                            }
                        }
                        break;
                    default:
                        System.out.println("功能尚在开发中....");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 文件浏览
    @Override
    public void scanDirection(File path) {//子目录地址
        //连接服务端
        try (Socket socket = new Socket("127.0.0.1", 8888);
             // 用来客户端读取服务端的数据
             InputStream netIn = socket.getInputStream();
             // 用来客户端给服务端发送数据
             OutputStream netOut = socket.getOutputStream();
        ) {
            /*
                获取了协议
                解析 : 拿到协议对象 , 协议对象中封装的是要做浏览数据 , 操作文件的路径
                scanDirProtocol对象 : scan , root
                scan  chid null null
            */
            String scan = AgreementUtil.getAgreement("SCAN", path.getPath(), null, null);
            //scan  Type=scan，FileName=root，Status=null,Message=message
            // 给服务端发送协议
            AgreementUtil.sendAgreement(netOut, scan);

            System.out.println("===============等待服务器给回应消息================");

            //接收消息
            BufferedReader br = new BufferedReader(new InputStreamReader(netIn));

            String content;
            //System.out.println("开始接收数据：");
            //scan 路径 ok null
            String firstLine = br.readLine();//协议 , 阻塞

            // 把服务端发送过来的一行协议字符串 , 封装成协议对象
            String type = AgreementUtil.getStatus(firstLine);
            // 获取协议对象中的状态
            if (type.equals("OK")) {
                // 成功
                // 服务端发过来的真正存储数据的file路径, 覆盖给current变量
                current = new File(AgreementUtil.getFileName(firstLine));
                System.out.println("当前目录：" + current);
                while ((content = br.readLine()) != null) {
                    System.out.println(content);
                }
            } else {
                // 失败
                System.out.println("浏览失败:" + AgreementUtil.getMessage(firstLine));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 文件下载
    @Override
    public void downloadFile(File file) {

        try (Socket socket = new Socket("127.0.0.1", 8888);
             // 用来客户端读取服务端的数据
             InputStream netIn = socket.getInputStream();
             // 用来客户端给服务端发送数据
             OutputStream netOut = socket.getOutputStream();
        ) {



            String down = AgreementUtil.getAgreement("DOWNLOAD", file.getPath(), null, null);
            // 给服务端发送协议
            AgreementUtil.sendAgreement(netOut, down);

            System.out.println("===============等待服务器给回应消息================");

            //接收消息
            BufferedReader br = new BufferedReader(new InputStreamReader(netIn));

            String content;
            //System.out.println("开始接收数据：");
            //scan 路径 ok null
            String firstLine = br.readLine();//协议 , 阻塞

            // 把服务端发送过来的一行协议字符串 , 封装成协议对象
            String type = AgreementUtil.getStatus(firstLine);
            // 获取协议对象中的状态
            if (type.equals("OK")) {
                String fileName = AgreementUtil.getFileName(firstLine);
                File saveFile = new File( downloadPath,fileName);
                try(FileOutputStream fos = new FileOutputStream(saveFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = netIn.read(buffer)) != -1) {
                        bos.write(buffer, 0, len);
                    }
                    bos.flush();
                    System.out.println(fileName+"下载成功");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            } else {
                // 失败
                System.out.println("下载失败:" + AgreementUtil.getMessage(firstLine));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }



    }

    // 文件上传   上传文件路径
    @Override
    public void uploadFile(File upFile) {
        System.out.println("准备上传文件: " + upFile.getAbsolutePath());
        System.out.println("文件大小: " + upFile.length() + " 字节");
        System.out.println("文件是否存在: " + upFile.exists());
        try (Socket socket = new Socket("127.0.0.1", 8888);
             // 用来客户端读取服务端的数据
             InputStream netIn = socket.getInputStream();
             // 用来客户端给服务端发送数据
             OutputStream netOut = socket.getOutputStream();){

            //System.out.println("已连接到服务器: " + socket.getInetAddress() + ":" + socket.getPort());
            Thread.sleep(50);

            String down = AgreementUtil.getAgreement("UPLOAD", upFile.getPath(), null, null);

            //System.out.println("发送协议: " + down);

            // 给服务端发送协议
            AgreementUtil.sendAgreement(netOut, down);
            try(FileInputStream fis = new FileInputStream(upFile);
            BufferedInputStream bis = new BufferedInputStream(fis)){
                byte[] buffer = new byte[4096];
                int len;
                //long totalSent = 0;
                System.out.println("--------"+"开始发送"+"----------------");
                while ((len = bis.read(buffer)) != -1) {
                    netOut.write(buffer, 0, len);
                    //totalSent += len;
                }
                netOut.flush();

                //System.out.println("文件数据发送完毕，总共发送: " + totalSent + " 字节");

                //System.out.println("关闭输出流...");

                socket.shutdownOutput();
                //System.out.println("输出流已关闭");


                System.out.println("--------"+"发送成功"+"----------------");
            }catch (Exception e) {
                throw new RuntimeException(e);
            }

            System.out.println("===============等待服务器给回应消息================");

            //接收消息
            BufferedReader br = new BufferedReader(new InputStreamReader(netIn));
            String firstLine = br.readLine();//协议 , 阻塞
            // 把服务端发送过来的一行协议字符串 , 封装成协议对象
                                           //System.out.println("收到服务器响应: " + firstLine);
            String type = AgreementUtil.getStatus(firstLine);
            // 获取协议对象中的状态
            if (type.equals("OK")) {
                String fileName = AgreementUtil.getFileName(firstLine);
                System.out.println(fileName+"上传成功");
            } else {
                // 失败
                System.out.println("上传失败:" + AgreementUtil.getMessage(firstLine));
            }
        } catch (IOException e) {
            //System.out.println("上传过程中发生异常: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }


/*    // 客户端上传代码修改
    @Override
    public void uploadFile(File upFile) {
        System.out.println("准备上传文件: " + upFile.getAbsolutePath());
        System.out.println("文件大小: " + upFile.length() + " 字节");
        System.out.println("文件是否存在: " + upFile.exists());

        try (Socket socket = new Socket("127.0.0.1", 8888);
             InputStream netIn = socket.getInputStream();
             OutputStream netOut = socket.getOutputStream()) {

            System.out.println("已连接到服务器: " + socket.getInetAddress() + ":" + socket.getPort());

            // 发送上传协议
            String down = AgreementUtil.getAgreement("UPLOAD", upFile.getPath(), null, null);
            System.out.println("发送协议: " + down);
            AgreementUtil.sendAgreement(netOut, down);

            try (FileInputStream fis = new FileInputStream(upFile);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {

                byte[] buffer = new byte[4096];
                int len;
                long totalSent = 0;

                System.out.println("开始发送文件数据...");

                while ((len = bis.read(buffer)) != -1) {
                    netOut.write(buffer, 0, len);
                    totalSent += len;
                    if (totalSent % (1024 * 1024) == 0) { // 每MB打印一次
                        System.out.println("已发送: " + (totalSent / 1024 / 1024) + "MB");
                    }
                }
                netOut.flush();
                System.out.println("文件数据发送完毕，总共发送: " + totalSent + " 字节");

                System.out.println("关闭输出流...");
                socket.shutdownOutput();
                System.out.println("输出流已关闭");
            }

            System.out.println("等待服务器响应...");
            BufferedReader br = new BufferedReader(new InputStreamReader(netIn));
            String firstLine = br.readLine();
            System.out.println("收到服务器响应: " + firstLine);

            String type = AgreementUtil.getStatus(firstLine);
            if (type.equals("OK")) {
                String fileName = AgreementUtil.getFileName(firstLine);
                System.out.println(fileName + "上传成功");
            } else {
                System.out.println("上传失败: " + AgreementUtil.getMessage(firstLine));
            }
        } catch (IOException e) {
            System.out.println("上传过程中发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }*/


}
