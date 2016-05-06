/* 
 * Copyright (c) 2008-2016 Sander van der Burg
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.nixos.disnix.service;
import org.freedesktop.dbus.*;
import org.freedesktop.dbus.exceptions.*;
import org.nixos.disnix.Disnix;
import javax.activation.*;
import java.io.*;
import java.util.*;

/**
 * Provides a SOAP interface to operations of the core Disnix service
 */
public class DisnixWebService
{
	/** Interface used to communicate to the core D-Bus service */
	private Disnix disnixInterface;
	
	/** Signal handler which waits and reacts on D-Bus signals of the Disnix core service */
	private DisnixSignalHandler handler;
	
	/** Contains the location of the directory that stores logfiles */
	private String logdir;
	
	/** 
	 * Creates a new SOAP interface instance.
	 * 
	 * @throws DBusException If the connection with the D-Bus service fails
	 */
	public DisnixWebService() throws DBusException
	{
		handler = new DisnixSignalHandler();
		
		System.out.println("Connecting to system bus");
		DBusConnection con = DBusConnection.getConnection(DBusConnection.SYSTEM);
		
		System.out.println("Register signal handlers");
		con.addSigHandler(Disnix.finish.class, handler);
		con.addSigHandler(Disnix.success.class, handler);
		con.addSigHandler(Disnix.failure.class, handler);
		
		System.out.println("Getting remote object");
		disnixInterface = (Disnix)con.getRemoteObject("org.nixos.disnix.Disnix", "/org/nixos/disnix/Disnix", Disnix.class);
		
		System.out.println("Getting logfile location");
		logdir = disnixInterface.get_logdir();
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#importm(String)
	 */
	public /*void*/ int importm(final String closure) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.importm(pid, closure);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#importLocalFile(String) 
	 */
	public /*void*/ int importLocalFile(DataHandler dataHandler) throws Exception
	{
		/* Generate temp file name */
		File tempFile = File.createTempFile("disnix_closure_", null);
		
		/* Save file on local filesystem */
		FileOutputStream fos = new FileOutputStream(tempFile);
		dataHandler.writeTo(fos);
		fos.flush();
		fos.close();
		
		/* Import the closure */
		importm(tempFile.toString());
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#export(String[])
	 */
	public String export(final String[] derivation) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.export(pid, derivation);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
			return ((Disnix.success)disnixThread.getSource()).derivation[0];
		else
			throw new Exception("Unknown event caught!"+disnixThread.getSource());
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#exportRemoteFile(String[])
	 */
	public DataHandler exportRemoteFile(final String[] derivation) throws Exception
	{
		/* First, export the closure */
		String closurePath = export(derivation);
		
		/* Create and return a data handler pointing to the export */
		return new DataHandler(new FileDataSource(closurePath));
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#printInvalid(String[])
	 */
	public String[] printInvalid(final String[] derivation) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.print_invalid(pid, derivation);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
			return ((Disnix.success)disnixThread.getSource()).derivation;
		else
			throw new Exception("Unknown event caught! "+disnixThread.getSource()); 
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#realise(String[])
	 */
	public String[] realise(final String[] derivation) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.realise(pid, derivation);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
			return ((Disnix.success)disnixThread.getSource()).derivation;
		else
			throw new Exception("Unknown event caught! "+disnixThread.getSource());
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#set(String, String)
	 */
	public /*void*/ int set(final String profile, final String derivation) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{	
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.set(pid, profile, derivation);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#queryInstalled(String)
	 */
	public String[] queryInstalled(final String profile) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.query_installed(pid, profile);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
			return ((Disnix.success)disnixThread.getSource()).derivation;
		else
			throw new Exception("Unknown event caught! "+disnixThread.getSource());
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#queryRequisites(String[])
	 */
	public String[] queryRequisites(final String[] derivation) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.query_requisites(pid, derivation);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
			return ((Disnix.success)disnixThread.getSource()).derivation;
		else
			throw new Exception("Unknown event caught! "+disnixThread.getSource());
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#collectGarbage(boolean)
	 */
	public /*void*/ int collectGarbage(final boolean deleteOld) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.collect_garbage(pid, deleteOld);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#activate(String, String, String, String[])
	 */
	public /*void*/ int activate(final String derivation, final String container, final String type, final String[] arguments) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.activate(pid, derivation, container, type, arguments);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#deactivate(String, String, String, String[])
	 */
	public /*void*/ int deactivate(final String derivation, final String container, final String type, final String[] arguments) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.deactivate(pid, derivation, container, type, arguments);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#lock(String)
	 */
	public /*void*/ int lock(final String profile) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.lock(pid, profile);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#unlock(String)
	 */
	public /*void*/ int unlock(final String profile) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.unlock(pid, profile);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#deleteState(String, String, String, String[])
	 */
	public /*void*/ int deleteState(final String derivation, final String container, final String type, final String[] arguments) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.delete_state(pid, derivation, container, type, arguments);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#snapshot(String, String, String, String[])
	 */
	public /*void*/ int snapshot(final String derivation, final String container, final String type, final String[] arguments) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.snapshot(pid, derivation, container, type, arguments);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#restore(String, String, String, String[])
	 */
	public /*void*/ int restore(final String derivation, final String container, final String type, final String[] arguments) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{	
					handler.addPid(pid, this);
					disnixInterface.restore(pid, derivation, container, type, arguments);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#queryAllSnapshots(String, String)
	 */
	public String[] queryAllSnapshots(final String container, final String component) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{	
					handler.addPid(pid, this);
					disnixInterface.query_all_snapshots(pid, container, component);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
			return ((Disnix.success)disnixThread.getSource()).derivation;
		else
			throw new Exception("Unknown event caught! "+disnixThread.getSource());
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#queryLatestSnapshot(String, String)
	 */
	public String[] queryLatestSnapshot(final String container, final String component) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.query_latest_snapshot(pid, container, component);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
			return ((Disnix.success)disnixThread.getSource()).derivation;
		else
			throw new Exception("Unknown event caught! "+disnixThread.getSource());
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#printMissingSnapshots(String[])
	 */
	public String[] printMissingSnapshots(final String[] component) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{	
					handler.addPid(pid, this);
					disnixInterface.print_missing_snapshots(pid, component);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
			return ((Disnix.success)disnixThread.getSource()).derivation;
		else
			throw new Exception("Unknown event caught! "+disnixThread.getSource());
	}
	
	private static void populatePathsVector(File[] paths, Vector<String> files)
	{
		for(File path : paths)
		{
			if(path.isDirectory())
			{
				File[] subPaths = path.listFiles();
				populatePathsVector(subPaths, files);
			}
			else
				files.add(path.getAbsolutePath());
		}
	}

	/**
	 * Exports all snapshots paths of files belonging to snapshots
	 * 
	 * @param derivation Name of the component to take snapshots from
	 * @return An array of absolute file paths
	 */
	public String[] exportRemoteSnapshotPaths(final String derivation)
	{
		File[] paths = { new File(derivation) };
		
		Vector<String> files = new Vector<String>();
		populatePathsVector(paths, files);
		
		String[] result = new String[files.size()];
		files.toArray(result);
		return result;
	}
	
	private static void populateFilesVector(File[] paths, Vector<File> files)
	{
		for(File path : paths)
		{
			if(path.isDirectory())
			{
				File[] subPaths = path.listFiles();
				populateFilesVector(subPaths, files);
			}
			else
				files.add(path);
		}
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#exportRemoteSnapshots(String[])
	 */
	public DataHandler[] exportRemoteSnapshots(final String derivation) throws Exception
	{
		File[] paths = { new File(derivation) };
		
		Vector<File> files = new Vector<File>();
		populateFilesVector(paths, files);
		
		/* Compose data handler for each file */
		DataHandler[] dataHandlers = new DataHandler[files.size()];
		
		for(int i = 0; i < files.size(); i++)
			dataHandlers[i] = new DataHandler(new FileDataSource(files.elementAt(i)));
		
		return dataHandlers;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#importSnapshots(String, String, String[])
	 */
	public int /*void*/ importSnapshots(final String container, final String component, final String[] snapshots) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{			
					handler.addPid(pid, this);
					disnixInterface.import_snapshots(pid, container, component, snapshots);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#importLocalSnapshots(String, String, String)
	 */
	public /*void*/ int importLocalSnapshots(final String container, final String component, String snapshot, DataHandler[] dataHandlers, String[] paths) throws Exception
	{
		/* Create temp directory */
		File tempDir = File.createTempFile("disnix_closure_", null);
		tempDir.delete();
		tempDir.mkdir();
		
		String snapshotName = new File(snapshot).getName();
		
		for(int i = 0; i < dataHandlers.length; i++)
		{
			String relativePath = paths[i].substring(snapshot.length());
			File targetFile = new File(tempDir.getAbsolutePath() + File.separator + snapshotName + File.separator + relativePath);
			
			if(targetFile.getParentFile() != null) // If the file is part of a directory then create it first
				targetFile.getParentFile().mkdirs();
			
			/* Save file on local filesystem */
			
			FileOutputStream fos = new FileOutputStream(targetFile);
			dataHandlers[i].writeTo(fos);
			fos.flush();
			fos.close();
		}
		
		/* Import the closure */
		String[] snapshots = { tempDir.getAbsolutePath() + File.separator + snapshotName };
		importSnapshots(container, component, snapshots);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#resolve_snapshots(int, String[])
	 */
	public String[] resolveSnapshots(final String[] snapshots) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.resolve_snapshots(pid, snapshots);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
			return ((Disnix.success)disnixThread.getSource()).derivation;
		else
			throw new Exception("Unknown event caught! "+disnixThread.getSource());
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#cleanSnapshots(int)
	 */
	public int /*void*/ cleanSnapshots(final int keep) throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.clean_snapshots(pid, keep);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		
		return 0;
	}
	
	/**
	 * @see org.nixos.disnix.client.DisnixInterface#captureConfig()
	 */
	public String captureConfig() throws Exception
	{
		final int pid = disnixInterface.get_job_id();
		
		DisnixThread disnixThread = new DisnixThread()
		{
			public void run()
			{
				try
				{
					handler.addPid(pid, this);
					disnixInterface.capture_config(pid);
					suspend();
					waitForNotificationToResume();
				}
				catch(InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		};
		Thread thread = new Thread(disnixThread);
		thread.start();
		thread.join();
		
		if(disnixThread.getSource() instanceof Disnix.failure)
			throw DisnixException.constructDisnixException(pid, logdir);
		else if(disnixThread.getSource() instanceof Disnix.success)
		{
			String line;
			String config = "";
			BufferedReader br = new BufferedReader(new FileReader(((Disnix.success)disnixThread.getSource()).derivation[0]));
			
			while((line = br.readLine()) != null)
				config += line + "\n";
			
			br.close();
			
			return config;
		}
		else
			throw new Exception("Unknown event caught!"+disnixThread.getSource());
	}
}
