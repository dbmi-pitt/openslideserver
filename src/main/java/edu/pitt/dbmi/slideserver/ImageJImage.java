package edu.pitt.dbmi.slideserver;

import ij.ImagePlus;
import ij.io.FileInfo;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Properties;


public class ImageJImage implements Slide {
	private ImagePlus image;
	private long age;
	private Properties info;
	private File file;
	private boolean labelAllowed = true;
	
	
	/**
	 * check if the file is valid ImageJ image
	 * @param path
	 * @return
	 */
	public static boolean isValid(String path){
		ImagePlus img = new ImagePlus(path);
		if(img.getOriginalFileInfo() != null){
			img.close();
			return true;
		}
		return false;
	}
	
	
	public ImageJImage(String path) throws IOException{
		this(new File(path));
	}
	
	public ImageJImage(File file) throws IOException{
		if(!file.exists() || !file.canRead() || !file.isFile())
			throw new IOException ("image "+file+" cannot be accessed.");
		
		image = new ImagePlus(file.getAbsolutePath());
		this.file = file;
		
		if(image.getOriginalFileInfo() == null)
			throw new IOException ("image "+file+" is not supported image.");
		
	}
	
	public void dispose() {
		image.close();
		info = null;
		image = null;
		file = null;
	}

	/**
	 * get slide label if available
	 * @param max
	 * @return
	 */
	public BufferedImage getLabel(int size){
		return null;
	}

	public BufferedImage getMacroImage() {
		return null;
	}

	public BufferedImage getMacroImage(int size) {
		return null;
	}

	public String getName() {
		return file.getName();
	}

	public String getPath() {
		return file.getParent();
	}

	public BufferedImage getRegion(int x, int y, int width, int height, int size) {
		// convert coordinates
		int w = size;
		int h = (height * w)/ width;
		
		// if region is horizontal
		if(height > width){
			h = size;
			w = (width * h)/ height;
		}
		
		BufferedImage buffer = new BufferedImage(w,h,getType());
				
		// scale this image
		buffer.createGraphics().drawImage(image.getImage(),0,0,w,h,x,y,x+width,y+height,Color.white,null);
		
		return buffer;
	}

	/**
	 * get buffered image type
	 * @return
	 */
	
	private int getType(){
		/*
		switch(image.getType()){
		case ImagePlus.GRAY8: return BufferedImage.TYPE_BYTE_GRAY;
		case ImagePlus.GRAY16: return BufferedImage.TYPE_USHORT_GRAY;
		case ImagePlus.GRAY32: return BufferedImage.TYPE_USHORT_GRAY;
		case ImagePlus.COLOR_RGB: return BufferedImage.TYPE_INT_RGB;
		case ImagePlus.COLOR_256: return BufferedImage.TYPE_BYTE_INDEXED;
		}
		*/
		//NOTE: ImageIO.write(JPEG) doesn't seem to support anything else
		// other then RGB, so I guess we are stuck :(
		return BufferedImage.TYPE_INT_RGB;
	}
	
	
	
	public Properties getSlideInfo() {
		
		if(info == null){
			FileInfo fi = image.getOriginalFileInfo();
			
			
			info = new Properties();
			info.setProperty("name",getName());
			info.setProperty("image.width",""+image.getWidth());
			info.setProperty("image.height",""+image.getHeight());
			info.setProperty("tile.width","256");
			info.setProperty("tile.height","256");
			info.setProperty("image.size",""+file.length());
			info.setProperty("pixel.size",""+fi.pixelWidth);
			
			info.setProperty("layer.count","1");
			info.setProperty("layer.0.width",""+image.getWidth());
			info.setProperty("layer.0.height",""+image.getHeight());
			
			info.setProperty("image.depth",""+image.getBitDepth());
			info.setProperty("image.mode",(ImagePlus.COLOR_256 == image.getType() || ImagePlus.COLOR_RGB == image.getType())?"color":"grayscale");
			
			
			// get all meta properties associated w
			for(Object key: image.getProperties().keySet())
				info.put(("image.meta."+key).toLowerCase(),image.getProperties().get(key));
			
			String format = "?";
			switch(fi.fileFormat){
			case FileInfo.RAW: format = "RAW"; break;
			case FileInfo.TIFF: format = "TIFF"; break;
			case FileInfo.GIF_OR_JPG: format = "GIF or JPEG"; break;
			case FileInfo.FITS: format = "FITS"; break;
			case FileInfo.BMP: format = "BMP"; break;
			case FileInfo.DICOM: format = "DICOM"; break;
			case FileInfo.ZIP_ARCHIVE: format = "ZIP"; break;
			case FileInfo.PGM: format = "PGM"; break;
			case FileInfo.IMAGEIO: format = "Image IO"; break;
			}
			info.setProperty("image.vendor","ImageJ");
			info.setProperty("image.format",format);
			
		}
		// update age
		age = System.currentTimeMillis();
		return info;
	}

	public BufferedImage getThumbnail(int max) {
		// update age
		age = System.currentTimeMillis();
		
		// check the max dimension
		if(image.getWidth() > max || image.getHeight() > max){
			int width = image.getWidth();
			int height = image.getHeight();
			
			if(width > height){
				height = (int)(((double)max * height) / width);
				width = max;
			}else{
				width = (int)(((double)max * width) / height);
				height = max;
			}
			// scale this image
			BufferedImage img = new BufferedImage(width, height,getType());
			img.getGraphics().setColor(Color.white);
			img.getGraphics().drawImage(image.getImage(),0,0,width,height,0,0,image.getWidth(),image.getHeight(),null);
			return img;
		}else
			return image.getBufferedImage();
	}

	/**
	 * is this slide old?
	 * that is last access time was a while ago
	 * @return
	 */
	public boolean isOld(){
		long time = System.currentTimeMillis() - age;
		return (time > MAX_AGE);
	}
	
	public void setLabelAllowed(boolean labelAllowed) {
		this.labelAllowed = labelAllowed;
	}

	public BufferedImage getLabel() {
		return getLabel(0);
	}
}
