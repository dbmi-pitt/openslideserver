package edu.pitt.dbmi.slideserver;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;


//import edu.cmu.cs.openslide.OpenSlide;
import org.openslide.AssociatedImage;
import org.openslide.OpenSlide;


/**
 *  @author Eugene Tseytlin (University of Pittsburgh)
 *  
 *  OpenSlideServer, is a digital slide image server that wrapps 
 *  OpenSlide library (http://openslide.cs.cmu.edu/) 
 *
 *  Copyright (c) 2007-2008 University of Pittsburgh
 *  All rights reserved.
 *
 *  OpenSlide is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, version 2.
 *
 *  OpenSlideServer is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with OpenSlideServer. If not, see <http://www.gnu.org/licenses/>.
 *
 *  Linking OpenSlideServer statically or dynamically with other modules is
 *  making a combined work based on OpenSlideServer. Thus, the terms and
 *  conditions of the GNU General Public License cover the whole
 *  combination.
 */
public class OpenSlideImage implements Slide {
	private static boolean generateFakeLabels = true;
	public static final String NO_LABEL = ".nolabel";
	//max age in milli seconds
	public static final int MAX_AGE = 5*60*1000; 
	private File file;
	private OpenSlide image;
	private Properties info;
	private long age;
	private boolean labelAllowed = true;
	
	
	/**
	 * if label is unavailable, should an image be generated
	 * @param path
	 * @return
	 */
	public static void setGenerateFakeLabels(boolean b){
		generateFakeLabels = b;
	}
	
	/**
	 * check if the file is valid ImageJ image
	 * @param path
	 * @return
	 */
	public static boolean isValid(String path){
		//return OpenSlide.fileIsValid(new File(path));
		return OpenSlide.detectVendor(new File(path)) != null;
	}
	
	/**
	 * create new slide instance
	 * @param path
	 */
	public OpenSlideImage(String path) throws IOException{
		this(new File(path));
	}
	
	/**
	 * create new slide instance
	 * @param path
	 */
	public OpenSlideImage(File f) throws IOException{
		this.file = f;
		if(!file.exists() || !file.canRead() || !file.isFile())
			throw new IOException ("slide "+file+" cannot be accessed.");
		//if(!OpenSlide.fileIsValid(file))
		if(OpenSlide.detectVendor(file) ==  null)	
			throw new IOException("slide "+file+" is not a valid whole slide image");
		image = new OpenSlide(file);
		// update age
		age = System.currentTimeMillis();
	}
	
	/**
	 * dispose of this image
	 */
	public void dispose(){
		image.dispose();
		//label= macro = null;
	}
	
	public String getName(){
		return file.getName();
	}
	
	public String getPath(){
		return file.getAbsolutePath();
	}
	
	public String toString(){
		return getName();
	}
	
	public void setLabelAllowed(boolean labelAllowed) {
		this.labelAllowed = labelAllowed;
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
	
	/**
	 * get slide metadata
	 * @return
	 */
	public Properties getSlideInfo(){
		if(info == null){
			info = new Properties();
			info.setProperty("name",getName());
			/*info.setProperty("comment",""+image.getComment());
			info.setProperty("image.width",""+image.getLayer0Width());
			info.setProperty("image.height",""+image.getLayer0Height());
			info.setProperty("tile.width","256");
			info.setProperty("tile.height","256");
			info.setProperty("layer.count",""+image.getLayerCount());
			for(int i=0;i<image.getLayerCount();i++){
				info.setProperty("layer."+i+".width",""+image.getLayerWidth(i));
				info.setProperty("layer."+i+".height",""+image.getLayerHeight(i));
			}*/
			info.setProperty("comment","");
			info.setProperty("image.width",""+image.getLevel0Width());
			info.setProperty("image.height",""+image.getLevel0Height());
			info.setProperty("tile.width","256");
			info.setProperty("tile.height","256");
			info.setProperty("layer.count",""+image.getLevelCount());
			for(int i=0;i<image.getLevelCount();i++){
				info.setProperty("layer."+i+".width",""+image.getLevelWidth(i));
				info.setProperty("layer."+i+".height",""+image.getLevelHeight(i));
			}
			
			info.setProperty("image.size",""+file.length());
			info.setProperty("pixel.size",calculatePixelSize());
			info.putAll(image.getProperties());
			info.setProperty("image.vendor",getVendor());
		}
		// update age
		age = System.currentTimeMillis();
		return info;
	}
	
	/**
	 * get thumbnail image of the slide
	 * @param max
	 * @return
	 */
	public BufferedImage getThumbnail(int max){
		// update age
		age = System.currentTimeMillis();
		try {
			return filterImage(image.createThumbnailImage(max));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
		//int width =  (int) image.getLayer0Width();
		//int height = (int) image.getLayer0Height();
		//return getRegion(0,0,width, height,max);
	}
	/**
	 * get slide label if available
	 * @param max
	 * @return
	 */
	public BufferedImage getLabel(){
		return getLabel(0);
	}
	/**
	 * get slide label if available
	 * @param max
	 * @return
	 */
	public BufferedImage getLabel(int size){
		BufferedImage label = null;
		
		if(isLabelAllowed()){
			label = getMetaImage("label");
			// if no label, cut it out from macro image
			if(label == null){
				BufferedImage m = getMetaImage("macro");
				if(m != null){
					label = createLabelFromMacro(m);
				}
			}
		}
		// if no label create fake
		if(label == null && generateFakeLabels)
			label = createFakeLabel();
				
		// update label size
		label = scaleImage(label, size);
		
		// update age
		age = System.currentTimeMillis();
		return label;
	}
	
	/**
	 * create fake label
	 * @return
	 */
	private BufferedImage createFakeLabel(){
		BufferedImage label = new BufferedImage(150,150,BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) label.getGraphics();
		g.setColor(Color.white);
		g.fillRect(0,0,label.getWidth(null),label.getHeight(null));
		g.setColor(Color.GRAY);
		g.setStroke(new BasicStroke(3));
		g.drawLine(0,0,150,150);
		g.drawLine(0,150,150,0);
		g.drawRect(0,0,149,149);
		return label;
	}
	
	/**
	 * Assuming that the label is on the left of a slide, cut
	 * it out from macro image
	 * @param img
	 * @return
	 */
	private BufferedImage createLabelFromMacro(BufferedImage img){
		int d = (img.getWidth() > img.getHeight())?img.getHeight():img.getWidth();
		BufferedImage label = new BufferedImage(d, d,BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) label.getGraphics();
		g.drawImage(img.getSubimage(0, 0, d, d), 0, 0, null);
		return label;
	}
	
	/**
	 * Assuming that the label is on the left of a slide, cut
	 * it out from macro image
	 * @param img
	 * @return
	 */
	private BufferedImage stripLabelFromMacro(BufferedImage img){
		int h = img.getHeight();
		int w = img.getWidth() - h;
		BufferedImage label = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) label.getGraphics();
		g.drawImage(img.getSubimage(h,0,w,h),0,0,null);
		return label;
	}
	
	/**
	 * get macro image associated with a slide.
	 * @return
	 */
	public BufferedImage getMacroImage(){
		return getMacroImage(0);
	}
	
	/**
	 * get macro image associated with a slide.
	 * @return
	 */
	public BufferedImage getMacroImage(int size){
		BufferedImage  macro = getMetaImage("macro");;
		
		// macro images in hammatsu contain labels
		if(!isLabelAllowed() && macro != null &&
			"hamamatsu".equalsIgnoreCase(getVendor())){
			macro = stripLabelFromMacro(macro);
		}
		
		// update label size
		macro = scaleImage(macro, size);
		
		// update age
		age = System.currentTimeMillis();
		return macro;
	}
	
	
	/**
	 * get meta image
	 * @param str
	 * @return
	 */
	private BufferedImage getMetaImage(String key){
		//Map<String,BufferedImage> imageMap = image.getAssociatedImages();
		Map<String,AssociatedImage> imageMap = image.getAssociatedImages();
		try {
			return (imageMap.containsKey(key))?filterImage(imageMap.get(key).toBufferedImage()):null;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * get arbitrary image region (in absolute coordinates)
	 * @param x 
	 * @param y
	 * @param width
	 * @param height
	 * @param power
	 * @return
	 */
	
	public BufferedImage getRegion(int x, int y, int width, int height, int size){
		// convert coordinates
		int w = size;
		int h = (height * w)/ width;
		// if region is horizontal
		if(height > width){
			h = size;
			w = (width * h)/ height;
		}
		
		double scale = ((double) w)/width;
		int sx = (int) (x * scale);
		int sy = (int) (y * scale);
	
		double power = 1 /scale;
		
		// init buffer if not there yet
		//if(buffer == null || buffer.getWidth() != w || buffer.getHeight() != h){
		BufferedImage buffer = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
		//}
		
		buffer.getGraphics().setColor(Color.white);
		buffer.getGraphics().fillRect(0,0,w,h);
		
		// draw new image
		try {
			image.paintRegion((Graphics2D)buffer.getGraphics(),0,0,sx,sy,w,h,power);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return buffer;
		
	}

	
	/**
	 * if necessary, strip alpha channel so that image would render correctly
	 * @param img
	 * @return
	 */
	private BufferedImage filterImage(BufferedImage src){
		if(src.getType() != BufferedImage.TYPE_INT_RGB){
			BufferedImage dst = new BufferedImage(src.getWidth(),
			src.getHeight(),BufferedImage.TYPE_INT_RGB);
			Graphics2D g = dst.createGraphics();
			g.drawImage(src,0,0,null);
			g.dispose();
			return dst;
		}
		return src;
	}
	
	
	/**
	 * if necessary, strip alpha channel so that image would render correctly
	 * @param img
	 * @return
	 */
	private BufferedImage scaleImage(BufferedImage src, int size){
		if(src == null || size <= 0)
			return src;
		
		int w = src.getWidth();
		int h = src.getHeight();
		// figure out aspect ratio
		if(w > h){
			// if size is the same, don't bother
			if(w == size)
				return src;
			
			h = (size * h)/w;
			w = size;
		}else{
			// if size is the same, don't bother
			if(h == size)
				return src;
			
			w = (size * w)/h;
			h = size;
		}
		BufferedImage dst = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dst.createGraphics();
		g.drawImage(src.getScaledInstance(w,h,Image.SCALE_SMOOTH),0,0,null);
		g.dispose();
		return dst;
	}

	/**
	 * get vendor
	 * @return
	 */
	private String getVendor(){
		return ""+image.getProperties().get("openslide.vendor");
	}
	
	
	/**
	 * calculate pixel size based on vendor
	 * @return
	 */
	private String calculatePixelSize(){
		String vendor = getVendor();
		try{
			// check if generic property is there
			if(image.getProperties().containsKey("openslide.mpp-x")){
				double mpp = Double.parseDouble(image.getProperties().get("openslide.mpp-x"));
				return ""+(mpp*0.001);
			}
			
			// else try to do format specific stuff
			if("hamamatsu".equalsIgnoreCase(vendor)){
				// get physical width in nanometers
				long pw = Long.parseLong(
				image.getProperties().get("hamamatsu.PhysicalWidth"));
				//long w  = image.getLayer0Width();
				long w  = image.getLevel0Width();
				// convert to mm
				return ""+((pw*0.000001)/w);
			}else if("aperio".equalsIgnoreCase(vendor)){
				// get microns per pixels
				double mpp = Double.parseDouble(
				image.getProperties().get("aperio.MPP"));
				//convert to mm
				return ""+(mpp*0.001);
			}else if("trestle".equalsIgnoreCase(vendor)){
				// get microns per pixels
				double mpp = Double.parseDouble(
				image.getProperties().get("tiff.YResolution"));
				//convert to mm
				return ""+(mpp*0.001);
			}else if("mirax".equalsIgnoreCase(vendor)){
				// get microns per pixels in the bottom most layer
				double mpp = Double.parseDouble(
				image.getProperties().get("mirax.LAYER_0_LEVEL_0_SECTION.MICROMETER_PER_PIXEL_X"));
				//convert to mm
				return ""+(mpp*0.001);
			}
		}catch(Exception ex){}
		return "?";
	}
	
	/**
	 * is label allowd for this image
	 * @return
	 */
	private boolean isLabelAllowed(){
		return labelAllowed && !(new File(file.getParentFile(),NO_LABEL)).exists();
	}
	
	
	public static void main(String [] args) throws IOException{
		OpenSlideImage img = new OpenSlideImage(new File("/home/tseytlin/Data/Images/HA_1449_ATYROS_2NA.vms"));
		System.out.println(img.getSlideInfo());
		BufferedImage image = img.getThumbnail(500);
		if(image == null){
			image = img.getRegion(0,0,18432,17152,500);
		}
		
		if(image != null){
			JOptionPane.showMessageDialog(null,new ImageIcon(image));
		}
	}
}
