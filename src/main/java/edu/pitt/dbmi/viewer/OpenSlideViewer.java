package edu.pitt.dbmi.viewer;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.TreeSet;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;

import edu.pitt.slideviewer.ImageProperties;
import edu.pitt.slideviewer.Viewer;
import edu.pitt.slideviewer.qview.QuickViewer;

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
public class OpenSlideViewer implements ActionListener{
	private final String OPEN_ICON = "/icons/Open16.gif";
	private final String PROP_ICON = "/icons/Preferences16.gif";
	private final String TAG_ICON = "/icons/Tag24.gif";
	private final String LBL_ICON = "/icons/Tag16.gif";
	private final String MAC_ICON = "/icons/Image16.gif";
	private final String LOGO_ICON = "/icons/Image48.png";
	private JFrame frame;
	private QuickViewer viewer;
	private File currentFile;
	
	public OpenSlideViewer(){
		frame = new JFrame();
		
		// create tag button
		JButton tag = new JButton(new ImageIcon(getClass().getResource(TAG_ICON)));
		tag.setToolTipText("View Digital Slide Label");
		tag.addActionListener(this);
		tag.setActionCommand("tag");
		tag.setPreferredSize(new Dimension(34,30));
		
		// init viewer
		viewer = new QuickViewer(null,QuickViewer.OPENSLIDE_TYPE);
		viewer.setConnectionManager(new LocalOpenSlideConnection(viewer));
		viewer.setSize(new Dimension(1000,700));
		viewer.getViewerControlPanel().addSeparator();
		viewer.getViewerControlPanel().add(tag);
		
		
		frame.setTitle("OpenSlideViewer");
		frame.setIconImage((new ImageIcon(getClass().getResource(LOGO_ICON)).getImage()));
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setJMenuBar(getMenuBar());
		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().add(viewer.getViewerPanel(),BorderLayout.CENTER);
		frame.pack();
		
		Dimension s = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension f = frame.getSize();
		frame.setLocation(new Point((int)((s.width-f.width)/2.0),(int)((s.height-f.height)/2.0)));
		frame.setVisible(true);
		
	}

	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		if("exit".equals(cmd)){
			System.exit(0);
		}else if("tag".equals(cmd)){
			Container cont = viewer.getInfoPanel();
			if(cont != null){
				JOptionPane.showMessageDialog(frame,cont,"Slide Information",JOptionPane.PLAIN_MESSAGE);
			}	
		}else if("open".equals(cmd)){
			JFileChooser chooser = new JFileChooser(currentFile);
			chooser.setFileFilter(new FileFilter() {
				public String getDescription() {
					return "OpenSlide Images (tif, bif, svs, vms, ndpi, mrxs, scn, svslide)";
				}
				public boolean accept(File f) {
					if(f.isDirectory())
						return true;
					
					// check valid extensions
					for(String ext: new String [] {".tif",".tiff",".svs",".vms",".ndpi",".bif",".jp2",".mrxs",".vmu",".scn",".svslide"}){
						if(f.getName().toLowerCase().endsWith(ext))
							return true;
					}
					return false;
				}
			});
			
			int r = chooser.showOpenDialog(frame);
			if(r == JFileChooser.APPROVE_OPTION){
				currentFile = chooser.getSelectedFile();
				if(currentFile.isFile()){
					open(currentFile.getAbsolutePath());
				}
			}
		}else if("prop".equals(cmd)){
			JEditorPane editor = new JEditorPane();
			editor.setContentType("text/html; charset=UTF-8");
			editor.setEditable(false);
			editor.setPreferredSize(new Dimension(500,500));
			ImageProperties ip = viewer.getImageProperties();
			if(ip != null){
				StringBuffer text  = new StringBuffer("<html><table width=100%>");
				text.append("<tr><td><b>image name:</b></td><td>"+ip.getName()+"</td></tr>");
				text.append("<tr><td><b>image size:</b></td><td>"+ip.getImageSize().width+" x "+ip.getImageSize().height+"</td></tr>");
				text.append("<tr><td><b>pixel size:</b></td><td>"+ip.getPixelSize()+"</td></tr>");
				text.append("<tr><td><b>image vendor:</b></td><td>"+ip.getSource()+"</td></tr><tr><td colspan=2><hr></td></tr>");
				for(Object k: new TreeSet(ip.getProperties().keySet())){
					text.append("<tr><td><b>"+k+":</b></td><td>"+ip.getProperties().get(k)+"</td></tr>");
				}
				text.append("</trable></html>");
				editor.setText(text.toString());
			}
			JOptionPane.showMessageDialog(frame,new JScrollPane(editor),"Slide Properties",JOptionPane.PLAIN_MESSAGE);
		}else if("label".equals(cmd)){
			ImageProperties ip = viewer.getImageProperties();
			if(ip != null){
				Image img = ip.getLabel();
				if(img != null){
					Dimension d = getReasonableSize(img.getWidth(null),img.getHeight(null));
					if(d != null){
						img = img.getScaledInstance(d.width,d.height, Image.SCALE_SMOOTH);
					}
					JOptionPane.showMessageDialog(frame,new JLabel(new ImageIcon(img)),"Slide Label",JOptionPane.PLAIN_MESSAGE);
				}
			}
		}else if("macro".equals(cmd)){
			ImageProperties ip = viewer.getImageProperties();
			if(ip != null){
				Image img = ip.getMacroImage();
				if(img != null){
					Dimension d = getReasonableSize(img.getWidth(null),img.getHeight(null));
					if(d != null){
						img = img.getScaledInstance(d.width,d.height, Image.SCALE_SMOOTH);
					}
					JOptionPane.showMessageDialog(frame,new JLabel(new ImageIcon(img)),"Slide Macro Image",JOptionPane.PLAIN_MESSAGE);
				}
			}
		}
	}

	
	private Dimension getReasonableSize(int width, int height){
		if(width > 1024 || height > 768){
			Dimension d = new Dimension(width,height);
			if(width > height){
				d.width = 1024;
				d.height = d.width*height/width;
			}else{
				d.height = 768;
				d.width = d.height*width/height;
			}
			return d;
		}
		return null;
	}
	
	private JMenuBar getMenuBar(){
		JMenuBar menu = new JMenuBar();
		JMenu file = new JMenu("File");
		JMenu view = new JMenu("View");
		
		JMenuItem open = new JMenuItem("Open Slide",new ImageIcon(getClass().getResource(OPEN_ICON)));
		open.addActionListener(this);
		open.setActionCommand("open");
		
		JMenuItem mcr = new JMenuItem("Macro Image",new ImageIcon(getClass().getResource(MAC_ICON)));
		mcr.addActionListener(this);
		mcr.setActionCommand("macro");
		
		JMenuItem lbl = new JMenuItem("Label Image",new ImageIcon(getClass().getResource(LBL_ICON)));
		lbl.addActionListener(this);
		lbl.setActionCommand("label");
		
		
		JMenuItem prop = new JMenuItem("Properties",new ImageIcon(getClass().getResource(PROP_ICON)));
		prop.addActionListener(this);
		prop.setActionCommand("prop");
		
		
		JMenuItem exit = new JMenuItem("Exit");
		exit.addActionListener(this);
		exit.setActionCommand("exit");
		
		menu.add(file);
		menu.add(view);
		file.add(open);
		file.addSeparator();
		file.add(exit);
		
		view.add(prop);
		view.addSeparator();
		view.add(lbl);
		view.add(mcr);
		
		
		return menu;
	}
	
	
	public void open(String str){
		try{
			File f = new File(str);
			viewer.openImage(f.getAbsolutePath());
			frame.setTitle("OpenSlideViewer ["+f.getName()+"]");
		}catch(Exception ex){
			JOptionPane.showMessageDialog(frame,"Can't open image "+str+"\n"+ex.getMessage(),
											"Error",JOptionPane.ERROR_MESSAGE);
			ex.printStackTrace();
		}
		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		OpenSlideViewer viewer = new OpenSlideViewer();
		if(args.length > 0)
			viewer.open(args[0].trim());
	}

}
