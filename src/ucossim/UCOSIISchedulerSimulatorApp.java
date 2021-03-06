/************************************************************************
 * Copyright (C) 2012  Jonah Murphy
 *
 *	This program is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either version 2
 *	of the License, or (at your option) any later version.
 *	
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *	
 *	You should have received a copy of the GNU General Public License
 *	along with this program; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * 
 * @file USCOSSIISchedulerSimulatorApp.java
 * @author Jonah Murphy
 * @date 01/02/2013
 * @brief
 ************************************************************************/
package ucossim;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.miginfocom.swing.MigLayout;


public class UCOSIISchedulerSimulatorApp extends JFrame{	
	private static final long serialVersionUID = 8876216890917889822L;
	
	//simulator related constants
	private static final String RUNNING_TASK_LBL_PREFIX = " HIGHEST PRIORITY TASK RUNNING: ";
	private static final int OSRDYGRP_BIT_WIDTH = 8;	// The number of bits in the the OSReadyGrp
	protected static final int IDLE_TASK_PRIO = OSRDYGRP_BIT_WIDTH * OSRDYGRP_BIT_WIDTH -1;
	private static int MAX_TICK_INCREMENTS = 83;		// The number of increments that make up a Tick
	private static int TICK_INCREMENT_TIME_MS = 20;		// The length of a tick increment in ms
	
	private Timer tickIncrementTimer;			// A timer to trigger an event every time a tick increment expires
								//triggering an event on a increment of tick allows us to update progress bar for visual effect..
	private int nIncrementCurrent = 0;			// Keeps track of what tick increment were at
	private UCOSIIKernel kernel;
	
	//UI related Constants
	private static final String APP_NAME = "uCOS-II Scheduler Simulator";
	private static final int APP_WIDTH = 585;
	private static final int APP_HEIGHT = 357;

	
	//UI widgets
	private JPanel mainPanel,  OSRdyGrpPanel, arrowsPanel, OSRdyTblPanel;
	private OSTblElement OSRdyGrpDisplayPanels[];   
	private OSTblElement OSRdyTblDisplayPanels[][];
	private JSpinner taskSpinner;					//TextField to enter a tasks priority to be enabled/disabled 
	private JButton enableOrDisableTaskBtn;				//button to enable / disable a task which is defined using the taskSpinner
	private JLabel timeTickLbl;					//Show the incremental progress of a timer tick..
	private JLabel runningTaskLbl;					//label to show which is the current task running

	private static Logger logger = Logger.getLogger(UCOSIISchedulerSimulatorApp.class.getName());

	/*
	 * Main entry point
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				UCOSIISchedulerSimulatorApp app = new UCOSIISchedulerSimulatorApp();
				app.setVisible(true);
			}
		});
		
	}
	
	/**
	 * 
	 */
	UCOSIISchedulerSimulatorApp() {
		initUI();
		
	}
	
	/**
	 * 
	 */
	public void initUI() {
		setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
		
		SpinnerModel taskSpinnerModel = new SpinnerNumberModel(0, 0, IDLE_TASK_PRIO, 1);
		taskSpinner = new JSpinner(taskSpinnerModel);
		
		enableOrDisableTaskBtn = new JButton("Enable / Disable Task");

		// set up simulators elements...
		OSRdyGrpPanel = new JPanel(new GridLayout(OSRDYGRP_BIT_WIDTH+1, 0, 0, 0));
		arrowsPanel = new JPanel(new GridLayout(OSRDYGRP_BIT_WIDTH+1, 0, 0, 0));
		OSRdyTblPanel = new JPanel(new GridLayout(OSRDYGRP_BIT_WIDTH+1,OSRDYGRP_BIT_WIDTH, 0, 0));

		// create ui table elements..
		OSRdyTblDisplayPanels = new OSRdyTblElement[OSRDYGRP_BIT_WIDTH][OSRDYGRP_BIT_WIDTH];
		OSRdyGrpDisplayPanels = new OSTblElement[OSRDYGRP_BIT_WIDTH];
		
		//Show the incremental progress of a timer tick..
		timeTickLbl = new JLabel("");
		
		//create a bold font..
		Font boldLabelFont = new Font(timeTickLbl.getFont().getName(),Font.BOLD,timeTickLbl.getFont().getSize());
		
		JLabel osRdyGrpLbl = new JLabel("OSRdyGrp");
		JLabel osRdyTblLbl =  new JLabel("OSRdyTbl");	
		osRdyGrpLbl.setFont(boldLabelFont);
		osRdyTblLbl.setFont(boldLabelFont);
		 
		// Create OSRdyGrp ui elements..
		OSRdyGrpPanel.add(new EmptyCell(Color.WHITE));
		OSRdyGrpPanel.add(new JColorLabel(Color.WHITE, " "));
		arrowsPanel.add(new EmptyCell(Color.WHITE));
		
		for (int column = 1; column < OSRdyGrpDisplayPanels.length+1; column++) {
			// create matrix elements
			OSRdyGrpDisplayPanels[column-1] = new OSTblElement(false, column);
			OSRdyGrpPanel.add(new JColorLabel(Color.WHITE, " "+(column-1))); //row label
			OSRdyGrpPanel.add(OSRdyGrpDisplayPanels[column-1]);
			arrowsPanel.add(new ImagePanel("arrow.jpg"));
		}
		
		//Add labels for cols of the OSRdyTbl
		for(int col = OSRdyTblDisplayPanels.length-1; col >= 0; col--) {
			OSRdyTblPanel.add(new JColorLabel(Color.WHITE, "    "+col));
		}

		// Create OSRdyTbl Matrix ui elements..
		for (int row = 0; row < OSRdyTblDisplayPanels.length; row++) {
			for (int column = OSRdyTblDisplayPanels[row].length - 1; column >= 0; column--) {
				OSRdyTblDisplayPanels[row][column] = new OSRdyTblElement(false, row* OSRDYGRP_BIT_WIDTH + column);
				OSRdyTblPanel.add(OSRdyTblDisplayPanels[row][column]);

				//Bind a mouseevent handler to each element of the OSRdyTbl..
				OSRdyTblDisplayPanels[row][column].addMouseListener(new OSRdyTblMouseAdapter());
			}
		}
	

		//Add all UI elements to the main panel
		mainPanel = new JPanel(new MigLayout("", "[grow][grow][grow][grow]"));
		mainPanel.setBackground(Color.WHITE);

		mainPanel.add(osRdyGrpLbl, "span 2");
		mainPanel.add(osRdyTblLbl, "span 2, wrap");

		mainPanel.add(OSRdyGrpPanel);
		mainPanel.add(arrowsPanel);
		mainPanel.add(OSRdyTblPanel, "span 2,wrap");

		runningTaskLbl = new JLabel(RUNNING_TASK_LBL_PREFIX);
		runningTaskLbl.setFont(boldLabelFont);
		runningTaskLbl.setForeground(new Color(0, 0, 255));
		mainPanel.add(runningTaskLbl, "cell 1 2");
		mainPanel.add(timeTickLbl, "span 2, wrap");

		JLabel taskNumberLbl = new JLabel(" Task #");
		taskNumberLbl.setFont(boldLabelFont);
		mainPanel.add(taskNumberLbl);
		mainPanel.add(taskSpinner, "grow");
		mainPanel.add(enableOrDisableTaskBtn, "grow, span 2, wrap");
		add(mainPanel);
	
		//bind action listnerers
		enableOrDisableTaskBtn.addActionListener(new ActionListener() {		
			@Override
			public void actionPerformed(ActionEvent arg0) {
				int priority = (int)taskSpinner.getValue();
				toggleTaskState(priority);
				
			}
		});
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setMinimumSize(new Dimension(APP_WIDTH, APP_HEIGHT));
		setSize(new Dimension(APP_WIDTH, APP_HEIGHT));
		setResizable(false);
		setTitle(APP_NAME);
		pack();
		
		kernel = new UCOSIIKernel();
		
		//The idle task must always be enabled!
		kernel.enableTask(IDLE_TASK_PRIO);
		OSRdyGrpDisplayPanels[OSRDYGRP_BIT_WIDTH-1].setElement();
		OSRdyTblDisplayPanels[OSRDYGRP_BIT_WIDTH-1][OSRDYGRP_BIT_WIDTH-1].setElement();

		tickIncrementTimer = new Timer();
		tickIncrementTimer.schedule(new TickIncrementHandler(),TICK_INCREMENT_TIME_MS);	
	}
	
	/**
	 * 
	 * @param priority
	 */
	public void toggleTaskState(int priority) {	
		if(!handlePriorityError(priority)) {
			return;
		}
		
		//get the OSTblRdyElement that needs to be updated
		OSTblElement osRdyTblElement = OSRdyTblDisplayPanels[priority / OSRDYGRP_BIT_WIDTH][priority % OSRDYGRP_BIT_WIDTH];	

		if (osRdyTblElement.isSet()) {
			//update the kernel
			kernel.disableTask(priority);
			
			//update the simulator ui
			OSRdyGrpDisplayPanels[priority / OSRDYGRP_BIT_WIDTH].clearElement();
			osRdyTblElement.clearElement();
		} else {
			//update the kernel
			kernel.enableTask(priority);
			
			//update the simulator ui
			OSRdyGrpDisplayPanels[priority / OSRDYGRP_BIT_WIDTH].setElement();
			osRdyTblElement.setElement();
		}
	}
	
	/**
	 * Check for errors with the priority level
	 * -display JOptionPane message dialog with relevant error message if neccesary
	 * @param priority
	 * @return false if an error is encountered otherwise true
	 */
	private boolean handlePriorityError(int priority) {
		//Error check the priority number
		if(priority < 0 || priority > IDLE_TASK_PRIO) {
			JOptionPane.showMessageDialog(null, "Task number must be greater than 0 or less than "+IDLE_TASK_PRIO, 
					"Invalid Task Number", JOptionPane.WARNING_MESSAGE);
			return false;
		}else if(priority == IDLE_TASK_PRIO) {
			JOptionPane.showMessageDialog(null, "You can't disable the idle task", 
					"Invalid Task Number", JOptionPane.WARNING_MESSAGE);
			return false;
			
		}
		return true;
	}

	/**
	 * MouseAdapter / handler for OSRdyTbl ui elements
	 * @author jonah
	 *
	 */
	private class OSRdyTblMouseAdapter extends MouseAdapter {
		@Override
		public void mouseReleased(MouseEvent e) {
			OSRdyTblElement osRdyTblElement = (OSRdyTblElement) e.getSource();	
			int priority = osRdyTblElement.getElementLocation();
			toggleTaskState(priority); 
		}	
	}
	
	/**
	 * Private inner class to handle the timer tick increment event.. 
	 * A timer tick increment represents a fractional part of a timer tick in a UCosII Kernel.
	 * The reason we need to trigger an event like this and not just on a tick is
	 * purely for visual effect on the simulator - i.e so we can visually show the time..
	 * @author jonah
	 *
	 */
	private class TickIncrementHandler extends TimerTask {
		@Override
		public void run() {
			//Reschedule the next tick increment!
			tickIncrementTimer.schedule(new TickIncrementHandler(),TICK_INCREMENT_TIME_MS);
			
			nIncrementCurrent++;
			
			//Have reached the edge of a tick?
			if (nIncrementCurrent < MAX_TICK_INCREMENTS) {
				timeTickLbl.setText(timeTickLbl.getText() + "|");
			} else {
				
				nIncrementCurrent = 0;
				timeTickLbl.setText("");
				
				int OSHighPrioCurrent = kernel.getCurrentRunningTaskPrio();
				int OSHighPrioRdy = kernel.OS_Sched();
				
				if(OSHighPrioCurrent != OSHighPrioRdy) {
					//Stop the current task, i.e update the simulator ui
					int py = OSHighPrioCurrent / OSRDYGRP_BIT_WIDTH;
					int px = OSHighPrioCurrent % OSRDYGRP_BIT_WIDTH;
					OSRdyTblDisplayPanels[py][px].setStopped();  
					OSRdyGrpDisplayPanels[py].setStopped();
					
					//Start the next task, i.e update the simulator ui..
					int y = OSHighPrioRdy / OSRDYGRP_BIT_WIDTH;
					int x = OSHighPrioRdy % OSRDYGRP_BIT_WIDTH;
					OSRdyTblDisplayPanels[y][x].setRunning();  
					OSRdyGrpDisplayPanels[y].setRunning();
					
					runningTaskLbl.setText(RUNNING_TASK_LBL_PREFIX + OSHighPrioRdy);
				}
			}
		}
	}
	
	/**
	 * Space filler for empty cells in GridLayout
	 *
	 */
	private class EmptyCell extends JLabel {
		private static final long serialVersionUID = 641494401719577323L;

		public EmptyCell(Color color) {
			super("");
			setBackground(color);
			setOpaque(true);
		}
	}
	
	
	private void setLookAndFeel(String lookAndFeel) {
		try {
			UIManager.setLookAndFeel(lookAndFeel);
		} catch (ClassNotFoundException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (InstantiationException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (IllegalAccessException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (UnsupportedLookAndFeelException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}
}
