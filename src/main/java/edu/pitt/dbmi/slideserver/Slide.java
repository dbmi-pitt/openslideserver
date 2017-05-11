package edu.pitt.dbmi.slideserver;

import java.awt.image.BufferedImage;
import java.util.Properties;




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
public interface Slide {
	public static final String NO_LABEL = ".nolabel";
	public static final int MAX_AGE = 5*60*1000; 

	/**
	 * dispose of this image
	 */
	public void dispose();
	
	public String getName();
	
	public String getPath();
	
	
	public void setLabelAllowed(boolean labelAllowed);

	
	/**
	 * is this slide old?
	 * that is last access time was a while ago
	 * @return
	 */
	public boolean isOld();
	
	/**
	 * get slide metadata
	 * @return
	 */
	public Properties getSlideInfo();
	
	/**
	 * get thumbnail image of the slide
	 * @param max
	 * @return
	 */
	public BufferedImage getThumbnail(int max);
	
	/**
	 * get slide label if available
	 * @param max
	 * @return
	 */
	public BufferedImage getLabel();
	
	/**
	 * get slide label if available
	 * @param max
	 * @return
	 */
	public BufferedImage getLabel(int size);
	
	
	/**
	 * get macro image associated with a slide.
	 * @return
	 */
	public BufferedImage getMacroImage();
	
	/**
	 * get macro image associated with a slide.
	 * @return
	 */
	public BufferedImage getMacroImage(int size);
	
	
	/**
	 * get arbitrary image region (in absolute coordinates)
	 * @param x 
	 * @param y
	 * @param width
	 * @param height
	 * @param power
	 * @return
	 */
	
	public BufferedImage getRegion(int x, int y, int width, int height, int size);
}
