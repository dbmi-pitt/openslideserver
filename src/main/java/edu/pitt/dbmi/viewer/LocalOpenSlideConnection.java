package edu.pitt.dbmi.viewer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.Timer;

//import edu.cmu.cs.openslide.OpenSlide;
import org.openslide.AssociatedImage;
import org.openslide.OpenSlide;
import edu.pitt.slideviewer.*;
import edu.pitt.slideviewer.policy.DiscreteScalePolicy;
import edu.pitt.slideviewer.qview.QuickNavigator;
import edu.pitt.slideviewer.qview.connection.ImageInfo;
import edu.pitt.slideviewer.qview.connection.Tile;
import edu.pitt.slideviewer.qview.connection.TileConnectionManager;
import edu.pitt.slideviewer.qview.connection.TileManager;
import edu.pitt.slideviewer.qview.connection.Utils;

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
public class LocalOpenSlideConnection extends TileConnectionManager implements ComponentListener {
	private OpenSlide imageFile;
	private Viewer viewer;
	private TileManager manager;
	protected ImageInfo info;
	protected Timer  resizeTimer, thumbnailTimer;
	
	
	public LocalOpenSlideConnection(Viewer v) {
		this.viewer = v;
            
        // create manager
        manager = new LocalOpenSlideTileManager(this);
        
        // init buffer
        buffer = new Tile();
        
        // init resize timer
        resizeTimer = new Timer(200,getResizeAction());
        resizeTimer.setRepeats(false);
        thumbnailTimer = new Timer(300,getThumnailFetchAction());
        thumbnailTimer.setRepeats(false);
	}
	
	/**
	 * Establish connection to the server and retrieve image info
	 */
	public void connect(String image) throws Exception {
		File file = new File(image);
		if (!file.exists() || !file.canRead() || !file.isFile())
			throw new IOException("slide " + file + " cannot be accessed.");
		//if (!OpenSlide.fileIsValid(file))
		if (OpenSlide.detectVendor(file) == null)	
			throw new IOException("slide " + file
					+ " is not a valid whole slide image");

		// create open slide
		imageFile = new OpenSlide(file);
		info = new ImageInfo();
		info.setName(file.getName());
		info.setConnectionManager(this);
		int iw = (int) imageFile.getLevel0Width();
		int ih = (int) imageFile.getLevel0Height();
		/*int iw = (int) imageFile.getLayer0Width();
		int ih = (int) imageFile.getLayer0Height();*/
		info.setImageSize(new Dimension(iw, ih));
		info.setTileSize(new Dimension(256, 256));

		// get microns per pixel and convert to mm per pixel
		try {
			info.setPixelSize(Double.parseDouble(calculatePixelSize()));
		} catch (NumberFormatException ex) {
			// info.setPixelSize(Constants.APERIO_PXL_SIZE);
		}

		Properties props = new Properties();
		props.putAll(imageFile.getProperties());
		info.setProperties(props);

		// /////////////////////////////////////////////
		// Because I in fact use regions and not blocks
		// I can get any resolution I want, so I might
		// as well have more levels
		// /////////////////////////////////////////////
		//ArrayList lvls = new ArrayList();
		ScalePolicy scalePolicy = new DiscreteScalePolicy();
		double[] scales = scalePolicy.getAvailableScales();
		int levels = scales.length;

		// iterate over scales
		for (int i = 0; i < levels; i++) {
			// honestly I don't care about dimensions
			int w = 0;
			int h = 0;
			double z = scales[i];
			if (z > 0.01) {
				ImageInfo.PyramidLevel lvl = new ImageInfo.PyramidLevel();
				lvl.setLevelSize(new Dimension(w, h));
				lvl.setZoom((scales == null) ? 1 / z : z);
				lvl.setLevelNumber(i);
				//lvls.add(lvl);
				info.addLevel(lvl);
			}
		}
		//info.setLevels((ImageInfo.PyramidLevel[]) lvls.toArray(new ImageInfo.PyramidLevel[0]));
		//info.sortLevels();

		// first get smaller image to shave off time, then get full size
		// image
		int w = viewer.getSize().width;
		int h = viewer.getSize().height;
		
		Image thumbnail = getImageThumbnail(new Dimension(w / 2, h / 2));
		if (thumbnail != null) {
			info.setThumbnail(thumbnail);
			info.scaleThumbnail(w, h);
			// resize temp buffer
			initBuffer();
			// fetch hi-res image
			thumbnailTimer.start();
		}
	}

	 /**
     * init temp buffer
     *
     */
    protected void initBuffer(){
    	Dimension d = viewer.getSize();
    	//Image img = viewer.getViewerComponent().createVolatileImage(d.width,d.height);
        BufferedImage img = new BufferedImage(d.width,d.height,BufferedImage.TYPE_INT_RGB);
		Graphics g = img.createGraphics();
		g.setColor(Color.white);
		g.fillRect(0,0,d.width,d.height);
        buffer.setImage(img);
    }
	
	/**
	 * get vendor
	 * 
	 * @return
	 */
	private String getVendor() {
		return imageFile.getProperties().get("openslide.vendor");
	}

	/**
	 * calculate pixel size based on vendor
	 * @return
	 */
	private String calculatePixelSize(){
		String vendor = getVendor();
		try{
			// check if generic property is there
			if(imageFile.getProperties().containsKey("openslide.mpp-x")){
				double mpp = Double.parseDouble(imageFile.getProperties().get("openslide.mpp-x"));
				return ""+(mpp*0.001);
			}
			
			if("hamamatsu".equalsIgnoreCase(vendor)){
				// get physical width in nanometers
				long pw = Long.parseLong(
				imageFile.getProperties().get("hamamatsu.PhysicalWidth"));
				long w  = imageFile.getLevel0Width();
				//long w  = imageFile.getLayer0Width();
				// convert to mm
				return ""+((pw*0.000001)/w);
			}else if("aperio".equalsIgnoreCase(vendor)){
				// get microns per pixels
				double mpp = Double.parseDouble(
				imageFile.getProperties().get("aperio.MPP"));
				//convert to mm
				return ""+(mpp*0.001);
			}else if("trestle".equalsIgnoreCase(vendor)){
				// get microns per pixels
				double mpp = Double.parseDouble(
						imageFile.getProperties().get("tiff.YResolution"));
				//convert to mm
				return ""+(mpp*0.001);
			}else if("mirax".equalsIgnoreCase(vendor)){
				// get microns per pixels in the bottom most layer
				double mpp = Double.parseDouble(
				imageFile.getProperties().get("mirax.LAYER_0_LEVEL_0_SECTION.MICROMETER_PER_PIXEL_X"));
				//convert to mm
				return ""+(mpp*0.001);
			}
		}catch(Exception ex){}
		return "?";
	}
	/**
	 * get image thumbnail of appropriate size (size of the current viewer
	 * window)
	 * 
	 * @return
	 */
	public synchronized Image getImageThumbnail() {
		Dimension d = (viewer != null) ? viewer.getSize() : new Dimension(500,500);
		return getImageThumbnail(d);
	}

	/**
	 * get image thumbnail of appropriate size (size of the current viewer
	 * window)
	 * 
	 * @return
	 */
	private synchronized Image getImageThumbnail(Dimension vsize) {
		if (info == null)
			return null;
		// long time = System.currentTimeMillis();
		int rotate = getTileManager().getRotateTransform();
		// Dimension is = Utils.getRotatedDimension(info.getImageSize(),rotate);
		Dimension is = info.getImageSize();
		// is original horizontal or vertical
		boolean isOrigHrz = (rotate % 2 == 0) ? info.isHorizontal() : info.isVertical();
		int w = vsize.width;
		int h = vsize.height;
		boolean hrz = Utils.isHorizontal(viewer.getSize(), is);

		int aw = (h * is.width) / is.height;
		int ah = (w * is.height) / is.width;
		int sz = (hrz) ? (!isOrigHrz) ? ah : w : (isOrigHrz) ? aw : h;

		if("mirax".equalsIgnoreCase(getVendor()))
			return getRegion(0,0,is.width,is.height,sz);
		return getThumbnail(sz);
	}

	/**
	 * get thumbnail image of the slide
	 * 
	 * @param max
	 * @return
	 */
	public BufferedImage getThumbnail(int max) {
		// update age
		try {
			return filterImage(imageFile.createThumbnailImage(max));
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * get slide label (if available)
	 * 
	 * @return null if not available
	 */
	public Image getImageLabel() {
		return getLabel();
	}

	/**
	 * get slide label if available
	 * 
	 * @param max
	 * @return
	 */
	public BufferedImage getLabel() {
		return getLabel(0);
	}

	/**
	 * get slide label if available
	 * 
	 * @param max
	 * @return
	 */
	public BufferedImage getLabel(int size) {
		BufferedImage label = null;

		if (isLabelAllowed()) {
			label = getMetaImage("label");
			// if no label, cut it out from macro image
			if (label == null) {
				BufferedImage m = getMetaImage("macro");
				if (m != null) {
					label = createLabelFromMacro(m);
				}
			}
		}
		// if no label create fake
		if (label == null)
			label = createFakeLabel();

		// update label size
		label = scaleImage(label, size);

		return label;
	}

	/**
	 * create fake label
	 * 
	 * @return
	 */
	private BufferedImage createFakeLabel() {
		BufferedImage label = new BufferedImage(150, 150,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) label.getGraphics();
		g.setColor(Color.white);
		g.fillRect(0, 0, label.getWidth(null), label.getHeight(null));
		g.setColor(Color.GRAY);
		g.setStroke(new BasicStroke(3));
		g.drawLine(0, 0, 150, 150);
		g.drawLine(0, 150, 150, 0);
		g.drawRect(0, 0, 149, 149);
		return label;
	}

	/**
	 * Assuming that the label is on the left of a slide, cut it out from macro
	 * image
	 * 
	 * @param img
	 * @return
	 */
	private BufferedImage createLabelFromMacro(BufferedImage img) {
		int d = (img.getWidth() > img.getHeight())?img.getHeight():img.getWidth();
		BufferedImage label = new BufferedImage(d, d,BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) label.getGraphics();
		if("leica".equalsIgnoreCase(getVendor()))
			g.drawImage(img.getSubimage(0,img.getHeight()-d, d, d), 0, 0, null);
		else
			g.drawImage(img.getSubimage(0, 0, d, d), 0, 0, null);
		return label;
	}

	/**
	 * Assuming that the label is on the left of a slide, cut it out from macro
	 * image
	 * 
	 * @param img
	 * @return
	 */
	private BufferedImage stripLabelFromMacro(BufferedImage img) {
		int h = img.getHeight();
		int w = img.getWidth() - h;
		BufferedImage label = new BufferedImage(w, h,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) label.getGraphics();
		g.drawImage(img.getSubimage(h, 0, w, h), 0, 0, null);
		return label;
	}

	/**
	 * get macro image associated with a slide.
	 * 
	 * @return
	 */
	public BufferedImage getMacroImage() {
		return getMacroImage(0);
	}

	/**
	 * get macro image associated with a slide.
	 * 
	 * @return
	 */
	public BufferedImage getMacroImage(int size) {
		BufferedImage macro = getMetaImage("macro");
		;

		// macro images in hammatsu contain labels
		if (!isLabelAllowed() && macro != null
				&& "hamamatsu".equalsIgnoreCase(getVendor())) {
			macro = stripLabelFromMacro(macro);
		}

		// update label size
		macro = scaleImage(macro, size);

		// update age
		return macro;
	}

	/**
	 * is label allowd for this image
	 * 
	 * @return
	 */
	private boolean isLabelAllowed() {
		return true;
	}

	/**
	 * get meta image
	 * 
	 * @param str
	 * @return
	 */
	private BufferedImage getMetaImage(String key) {
		Map<String, AssociatedImage> imageMap = imageFile.getAssociatedImages();
		try {
			return (imageMap.containsKey(key)) ? filterImage(imageMap.get(key).toBufferedImage()): null;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * get arbitrary image region (in absolute coordinates)
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param power
	 * @return
	 */

	public BufferedImage getRegion(int x, int y, int width, int height, int size) {
		// convert coordinates
		int w = size;
		int h = (height * w) / width;
		// if region is horizontal
		if (height > width) {
			h = size;
			w = (width * h) / height;
		}

		double scale = ((double) w) / width;
		int sx = (int) (x * scale);
		int sy = (int) (y * scale);

		double power = 1 / scale;

		// init buffer if not there yet
		// if(buffer == null || buffer.getWidth() != w || buffer.getHeight() !=
		// h){
		BufferedImage buffer = new BufferedImage(w, h,BufferedImage.TYPE_INT_RGB);
		// }

		buffer.getGraphics().setColor(Color.white);
		buffer.getGraphics().fillRect(0, 0, w, h);

		// draw new image
		try {
			imageFile.paintRegion((Graphics2D) buffer.getGraphics(), 0, 0, sx, sy,w, h, power);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return buffer;

	}

	/**
	 * if necessary, strip alpha channel so that image would render correctly
	 * 
	 * @param img
	 * @return
	 */
	private BufferedImage filterImage(BufferedImage src) {
		if (src.getType() != BufferedImage.TYPE_INT_RGB) {
			BufferedImage dst = new BufferedImage(src.getWidth(), src
					.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = dst.createGraphics();
			g.drawImage(src, 0, 0, null);
			g.dispose();
			return dst;
		}
		return src;
	}

	/**
	 * if necessary, strip alpha channel so that image would render correctly
	 * 
	 * @param img
	 * @return
	 */
	private BufferedImage scaleImage(BufferedImage src, int size) {
		if (src == null || size <= 0)
			return src;

		int w = src.getWidth();
		int h = src.getHeight();
		// figure out aspect ratio
		if (w > h) {
			// if size is the same, don't bother
			if (w == size)
				return src;

			h = (size * h) / w;
			w = size;
		} else {
			// if size is the same, don't bother
			if (h == size)
				return src;

			w = (size * w) / h;
			h = size;
		}
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dst.createGraphics();
		g.drawImage(src.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0,0, null);
		g.dispose();
		return dst;
	}
	
	
	public static class LocalOpenSlideTileManager extends TileManager{
		private LocalOpenSlideConnection connection;
		public LocalOpenSlideTileManager(LocalOpenSlideConnection c) {
			connection = c;
		}

		/**
		 * Grant work of fetching tiles
		 */
		public Tile fetchTile(Rectangle r, Dimension tileSize, int delay) {
			// get scale
			ImageInfo info = connection.getImageInfo();
			
			if(info == null)
				return null;
			
			//Dimension tileSize = info.getTileSize();
			double scale = tileSize.getWidth()/r.width;
			
			 // fetch tile
		    BufferedImage img = connection.getRegion(r.x,r.y,r.width,r.height,tileSize.width);
	        
		    // compress it as JPEG (this is stupid, but required(
		    ByteArrayOutputStream out = new ByteArrayOutputStream();
		    try{
		    	ImageIO.write(img,"jpeg",out);
		    }catch(Exception ex){
		    	return null;
		    }
	        
	        Tile tile = new Tile();
	        tile.setData(out.toByteArray());
	        tile.setBounds(r);
	        tile.setScale(scale);
	        return tile;
	    }

		public TileConnectionManager getTileConnectionManager() {
			return connection;
		}
	}


	public ImageInfo getImageInfo() {
		return info;
	}

	public TileManager getTileManager() {
		return manager;
	}

	public Viewer getViewer() {
		return viewer;
	}

	public ComponentListener getComponentListener() {
		return this;
	}
	
	 /**
     * get resize action
     * @return
     */
    private ActionListener getResizeAction(){
    	return new ActionListener(){
			public void actionPerformed(ActionEvent a) {
				//we need to refetch thumb image
		        if(viewer.hasImage()){
	        	
		            //  get thumbnail
		        	int w = viewer.getSize().width;
		            int h = viewer.getSize().height;
		            
		           
		            // scale thumbnail as temp measure
		            info.scaleThumbnail(w, h);
		            
		            // resize temp buffer
		            initBuffer();
		            
		            // resize navigator
		            ((QuickNavigator)viewer.getNavigator()).setImage(info);
		            
		            //data = null;
		            double scl = viewer.getScale();
		            if(scl <= viewer.getMinimumScale()){
		            	viewer.getViewerController().resetZoom();
		            }else{
		            	Rectangle r = viewer.getViewRectangle();
		            	viewer.setViewRectangle(new Rectangle(r.x,r.y,(int)(w/scl),(int)(h/scl)));
		            }
		            viewer.repaint();
		            
		            // send resize event
		            viewer.firePropertyChange(Constants.VIEW_RESIZE,null,viewer.getSize());
		        
		            // stop resize timer
		            resizeTimer.stop();
		            
		            // request hi-res thumbnail
		            thumbnailTimer.start();
		        }   
			}
    	};
    }
    
    /**
     * get resize action
     * @return
     */
    private ActionListener getThumnailFetchAction(){
    	return new ActionListener(){
			public void actionPerformed(ActionEvent a) {
				Image t = getImageThumbnail();
        		if(t != null){
        			info.setThumbnail(t);
	                // resize navigator
	                ((QuickNavigator)viewer.getNavigator()).setImage(info);
	                viewer.repaint();
	            }else if(viewer != null)
	                System.err.println("Error: Could not get thumbnail for "+viewer.getImage()); 
        		thumbnailTimer.stop();
			}
    	};
    }
    public void componentHidden(ComponentEvent e){}
    public void componentMoved(ComponentEvent e){}
    public void componentShown(ComponentEvent e){}
    /**
     * Listen for componet being resized
     */
    public void componentResized(ComponentEvent evt){
    	resizeTimer.start();
    }
    
    //disconnect
    public void disconnect() {
    	super.disconnect();
    	if(imageFile != null)
    		imageFile.dispose();
    	info = null;
    }

    // release resources
    public void dispose(){
    	super.dispose();
    	manager = null;
        viewer = null;
    }


}
