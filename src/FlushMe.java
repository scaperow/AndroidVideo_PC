//收发在同一个线程11111111111111
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class FlushMe extends Frame {
	private static final long serialVersionUID = 1L;
	private BufferedImage im;
	// 图像信息

	//240*160 dataLen=57600 iOneFrameData=576  
	//100*100 dataLen=15000 iOneFrameData=150  
	private static final int width = 240;//240;
	private static final int height = 160;//160;
	private int iTotalFramePerImage=20;//100;//一幅图像分iTotalFramePerImage次发送
	
	private static final int dataLen = width*height*3/2;
	private int iOneFrameData=dataLen/iTotalFramePerImage;
	private int iNumFrame=0;
	private int numImage=0;
	
	//240*160---57600//307200 OR 230400//57600 76800
	//原始的YUV420(Y41P)数据  一幅100*50大小的图像需要的缓存空间为100*50*1.5

	private static final int numBands = 3;
	public byte[] testData=new byte[10];
	// 图像数组
	private byte[] byteArray = new byte[width * height * numBands];// 图像RGB数组
	private byte[] yuv420sp = new byte[dataLen];// 图像YUV数组 240*160*3/2
	
	private static final int[] bandOffsets = new int[] { 0, 1, 2 };
	private static final SampleModel sampleModel = new PixelInterleavedSampleModel(
			DataBuffer.TYPE_BYTE, width, height, 3, width * 3,
			bandOffsets);
	// ColorModel
	private static final ColorSpace cs=ColorSpace.getInstance(ColorSpace.CS_sRGB);
	private static final ComponentColorModel cm=new ComponentColorModel(cs, false, false,
			Transparency.OPAQUE, DataBuffer.TYPE_BYTE);

	public FlushMe() {
		super("Flushing");
		updateIM();
		setSize(width, height);
		// 窗口关闭方法
		this.addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosing(java.awt.event.WindowEvent e) {
				System.exit(0);
			}
		});
		// 窗口居中
		this.setLocationRelativeTo(null);
		this.setResizable(false);
		this.setVisible(true);
		this.getData();
	}
	@Override
	public void update(Graphics g){
		paint(g);
	}
	@Override
	public void paint(Graphics g) {
		g.drawImage(im, 0, 0, width, height, this);
	}
	
	public void getData() {
		try {
			//System.out.println("camera connected111...");
			ServerSocket server = new ServerSocket(8899);
			Socket socket = server.accept();
			System.out.println("camera connected...");
			
			ServerSocket server_send = new ServerSocket(7788);//建立转发的socket
			Socket socket_send = server_send.accept();
			System.out.println("player connected...");
			
			DataInputStream dis = new DataInputStream(socket.getInputStream());
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			
			DataInputStream dis_send = new DataInputStream(socket_send.getInputStream());
			DataOutputStream dos_send = new DataOutputStream(socket_send.getOutputStream());
		
			while (true) {

				int iSendByte;
				 numImage++;
				 System.out.println("numImage="+numImage);
				//System.out.println("begin picture");
				for(iNumFrame=0;iNumFrame<iTotalFramePerImage;iNumFrame++)
				{
					iSendByte=dis.read(yuv420sp, iNumFrame*iOneFrameData, iOneFrameData);
					dos.writeBoolean(true);
					System.out.println(iSendByte);
				}
				//System.out.println("end picture");
				
				System.out.println("begin transmiting");
				for(iNumFrame=0;iNumFrame<iTotalFramePerImage;iNumFrame++)//转发到手机端player
				{
					dos_send.write(yuv420sp,iNumFrame*iOneFrameData,iOneFrameData);
					dis_send.readBoolean();
					
				}
				System.out.println("end transmiting");
				
				
				updateIM();
				im.flush();
				repaint();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void updateIM() {
		try {
			// 解析YUV成RGB格式
			decodeYUV420SP(byteArray, yuv420sp, width, height);

			DataBuffer dataBuffer = new DataBufferByte(byteArray, numBands);
			WritableRaster wr = Raster.createWritableRaster(sampleModel,
					dataBuffer, new Point(0, 0));
			im = new BufferedImage(cm, wr, false, null);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void decodeYUV420SP(byte[] rgbBuf, byte[] yuv420sp,
			int width, int height) {
		final int frameSize = width * height;
		if (rgbBuf == null)
			throw new NullPointerException("buffer 'rgbBuf' is null");
		if (rgbBuf.length < frameSize * 3)
			throw new IllegalArgumentException("buffer 'rgbBuf' size "
					+ rgbBuf.length + " < minimum " + frameSize * 3);

		if (yuv420sp == null)
			throw new NullPointerException("buffer 'yuv420sp' is null");

		if (yuv420sp.length < frameSize * 3 / 2)
			throw new IllegalArgumentException("buffer 'yuv420sp' size "
					+ yuv420sp.length + " < minimum " + frameSize * 3 / 2);

		int i = 0, y = 0;
		int uvp = 0, u = 0, v = 0;
		int y1192 = 0, r = 0, g = 0, b = 0;
		for (int j = 0, yp = 0; j < height; j++) {
			uvp = frameSize + (j >> 1) * width;
			u = 0;
			v = 0;
			for (i = 0; i < width; i++, yp++) {
				if (y < 0)
					y = 0;

				rgbBuf[yp * 3] = (byte) y;
				rgbBuf[yp * 3 + 1] = (byte) y;
				rgbBuf[yp * 3 + 2] = (byte) y;
				y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0)
					y = 0;
	
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}
				

				y1192 = 1192 * y;
				r = (y1192 + 1634 * v);
				g = (y1192 - 833 * v - 400 * u);
				b = (y1192 + 2066 * u);

				if (r < 0)
					r = 0;
				else if (r > 262143)
					r = 262143;
				if (g < 0)
					g = 0;
				else if (g > 262143)
					g = 262143;
				if (b < 0)
					b = 0;
				else if (b > 262143)
					b = 262143;
				
				
				//在这里转换成byte在手机上显示时  比较亮的部分解码效果很差，呈现黄色块状，可能是因为颜色信息丢失的原因
				rgbBuf[yp * 3] = (byte) (r >> 10);
				rgbBuf[yp * 3 + 1] = (byte) (g >> 10);
				rgbBuf[yp * 3 + 2] = (byte) (b >> 10);
			}
		}
	}

	public static void main(String[] args) {
		new FlushMe();
	}
}