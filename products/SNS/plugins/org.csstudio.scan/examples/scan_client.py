"""
Scan Client Tools

Helpers for connecting to the Scan Server,
to assemble commands for a scan,
submit them to the server,
monitor the execution.

Shortcuts for 1D, 2D, *D scans.

This code depends on the basic org.csstudio.scan.* packages.
It can be invoked from Jython scripts associated with BOY displays
but also from jython command lines outside of CSS.

@author: Kay Kasemir
"""
import sys, os, glob

# -------------------------------------------------------
# Path setup
#
# 1)  When running under Eclipse (from BOY displays),
#     path variables will auto-configure from the plugin registry.
#
# 2a) When running outside of Eclipse
#     (jython command-line, Matlab, also scripts executed from PyDev)
#     either set the path to the standalone scan.client.jar
#     (generated by org.csstudio.scan.client/build.xml)
scan_client_jar="/home/css/scan.client.jar"

# 2b) ... or give the path to the plugin directory
plugin_install_location="../../"

# Try to resolve the paths when running outside of Eclipse
scan_plugin = None
client_plugin = None
try:
    # Look for "org.csstudio.scan/bin" when running with IDE and plugin source,
    # or "org.csstudio.scan_{version}[.jar]" for exported product
    if os.path.exists(plugin_install_location + "org.csstudio.scan/bin"):
        scan_plugin = plugin_install_location + "org.csstudio.scan/bin"
        client_plugin = plugin_install_location + "org.csstudio.scan.client/bin"
    else:
        scan_plugin = glob.glob(plugin_install_location + "org.csstudio.scan_*")[0]
        client_plugin = glob.glob(plugin_install_location + "org.csstudio.scan.client*")[0]
except:
    pass # Ignore. May use scan_client_jar or be running under Eclipse

# When running under Eclipse, replace settings with info from registry
def __getPluginPath__(plugin):
    """ Get path to classes in plugin (if running under Eclipse)
        @param plugin Plugin ID
        @return Path to the classes in the plugin, or None
    """
    try:
        # Under Eclipse, should be able to locate the bundle
        from org.eclipse.core.runtime import Platform
        from org.eclipse.core.runtime import FileLocator
        from org.eclipse.core.runtime import Path
        bundle = Platform.getBundle(plugin)
        # While in the IDE, the classes are in a bin subdir
        url = FileLocator.find(bundle, Path("bin"), None)
        if url:
            return FileLocator.resolve(url).getPath()
        # In an exported product, the classes are
        # at the root of the jar or the expanded
        # plugin directory
        return FileLocator.getBundleFile(bundle).getPath()
    except:
        # Not running under Eclipse
        return None

path = __getPluginPath__("org.csstudio.scan")
if path:
    scan_plugin = path
path = __getPluginPath__("org.csstudio.scan.client")
if path:
    client_plugin = path


# Now one of the possibilities should work out...
if scan_plugin and os.path.exists(scan_plugin) and client_plugin and os.path.exists(client_plugin):
    sys.path.append(scan_plugin)
    sys.path.append(client_plugin)
elif os.path.exists(scan_client_jar):
    sys.path.append(scan_client_jar)
else:
    raise Exception("Scan client library not configured")

# When running inside Eclipse, also add Scan UI to path
path = __getPluginPath__("org.csstudio.scan.ui")
if path:
    sys.path.append(path)

# Example for displaying debug info:
#from org.eclipse.jface.dialogs import MessageDialog
# for p in sys.path:
#    print p
#    MessageDialog.openWarning(None, "Debug", "Using " + p)

# -------------------------------------------------------
# Scan Server connection setup

import org.csstudio.scan.server.ScanServer as ScanServer
import java.lang.System as System
    
# Set scan server host and port if they're not the default.
# Can also pass this as command-line definitions to jython:
#  jython  -DScanServerHost=ky9linux.ornl.gov ....
#System.setProperty(ScanServer.HOST_PROPERTY, "ky9linux.ornl.gov")
#System.setProperty(ScanServer.PORT_PROPERTY, str(4810))


# -------------------------------------------------------
# Leave rest as is

# Python packages are different from Java Packages
# There can be issues with 'package scanning' that cause
# jython to not find classes when using
#   from org.csstudio.scan.command import *
# or
#   import org.csstudio.scan.command
#
# The most dependable way is to explicitly import one-by-one
import org.csstudio.scan.client.ScanServerConnector as ScanServerConnector
import org.csstudio.scan.command.CommandSequence as CommandSequence
import org.csstudio.scan.command.LoopCommand as LoopCommand
import org.csstudio.scan.command.Comparison as Comparison
import org.csstudio.scan.command.ScanCommand as ScanCommand
import org.csstudio.scan.command.WaitCommand as WaitCommand
import org.csstudio.scan.command.DelayCommand as DelayCommand
import org.csstudio.scan.command.LogCommand as LogCommand
import org.csstudio.scan.command.SetCommand as SetCommand
import org.csstudio.scan.data.SpreadsheetScanDataIterator as SpreadsheetScanDataIterator

import time


class ScanClient(object):
    """
    Base class for a scan client
    
    Can submit scans to the server and monitor them
    """
    def __init__(self):
        # Connection to the scan server
        self.server = ScanServerConnector.connect()
        # Scan ID
        self.id = -1
        
    def checkServer(self):
        """
        Attempt to call the server, and try to re-connect on error.
        
        The server could be restarted, or there could have been
        a network issue between the time we originally connected
        to the server and now, which would invalidate the original
        server connection.
        """
        try:
            self.server.getInfo()
        except:
            self.server = ScanServerConnector.connect()

    def submit(self, name, commands):
        """
        Submit a CommandSequence to the server for execution
        
        @param name  Name of the scan
        @param commands  CommandSequence or string with XML text
          
        @return Scan ID
        """
        self.checkServer()
        if isinstance(commands, str):
            xml = commands
        elif isinstance(commands, CommandSequence):
            xml = commands.getXML()
        else:
            raise Exception('Expecting CommandSequence or XML-text')
        self.id = self.server.submitScan(name, xml)
        return self.id

    def getScanInfo(self, id=-1):
        """
        Get scan info
        
        @param id Scan ID, defaulting to the last submitted scan
        """
        self.checkServer()
        if id == -1:
            id = self.id
        return self.server.getScanInfo(id)
    
    def printData(self, id=-1, *devices):
        """
        Print scan data
        
        @param id Scan ID, defaulting to the last submitted scan
        @param devices One or more device names. Default: All devices in scan.
        """
        self.checkServer()
        if id == -1:
            id = self.id
        data = self.server.getScanData(id)
        if devices:
            sheet = SpreadsheetScanDataIterator(data, devices)
        else:
            sheet = SpreadsheetScanDataIterator(data)
        sheet.dump(System.out)
            
    def waitUntilDone(self, id=-1):
        """
        Wait until a submitted scan has finished
        
        @param id Scan ID, defaulting to the last submitted scan
        """
        while True:
            info = self.getScanInfo(id)
            print info
            if info.getState().isDone():
                break;
            time.sleep(1.0)
            
    def __str__(self):
        return "Scan client, connected to %s" % self.server.getInfo()



class ScanNd(ScanClient):
    """
    N-dimensional scan that logs arbitrary number of readings
    based on nested loops.
    
    Arguments:
    
    * First argument can be scan name. Optional.
    * Loop specification for all following arguments: ('device', start, end[, step])
    * Names of device to log in addition to loop'ed devices
    * Basic ScanCommand to perform: SetCommand, WaitCommand, ...
    
    Examples:
    
    # Scan 'xpos' from 1 to 10, stepping 1
    scan('My first one', ('xpos', 1, 10) )
    # Scan name is optional
    scan( ('xpos', 1, 10) )

    # Log the 'readback' (xpos is logged automatically)
    scan( ('xpos', 1, 10), 'readback')
    
    # Scan 'xpos' from 1 to 10, stepping 1,
    # inside that looping 'ypos' from 1 to 5 by 0.2,
    # logging 'readback'
    scan('XY Example', ('xpos', 1, 10), ('ypos', 1, 5, 0.2), 'readback')

    # Scan 'xpos' and 'ypos', set something to '1' and then '3' (with readback)
    scan('XY Example', ('xpos', 1, 10), ('ypos', 1, 5, 0.2),
         SetCommand('setpoint', 1, 'readback'),
         SetCommand('setpoint', 3, 'readback'))
    """
    
    def __init__(self):
        ScanClient.__init__(self)

    def _decodeScan(self, parms):
        """ Check for 
                ('device', start, end, step)
             or 
                ('device', start, end)
             for a default step size of 1
             @return ('device', start, end, step)
        """
        if (len(parms) == 4):
            return (parms[0], parms[1], parms[2], parms[3])
        elif (len(parms) == 3):
            return (parms[0], parms[1], parms[2], 1)
        else:
            raise Exception('Scan parameters should be (''device'', start, end, step), not %s' % str(parms)) 
    
    def __call__(self, *args):
        """ N-dimensional scan command.
            @return ID of scan that was scheduled on the scan server
        """
        # Turn args into modifyable list
        args = list(args)
        
        # First string is optional scan title
        if len(args) > 0  and  isinstance(args[0], str):
            name = args[0]
            args.pop(0)
        else:
            name = "Scan"

        # Work backwards, starting with 'inner' loop
        cmds = []
        while len(args) > 0:
            arg = args.pop()
            if isinstance(arg, tuple):
                scan = self._decodeScan(arg)
                cmds = [ LoopCommand(scan[0], scan[1], scan[2], scan[3], cmds) ]
            elif isinstance(arg, ScanCommand):
                cmds.insert(0, arg)
            elif isinstance(arg, str):
                # If the 'current' command is already a log command, extend it
                if len(cmds) > 0  and  isinstance(cmds[len(cmds)-1], LogCommand):
                    log = cmds[len(cmds)-1]
                    devices = list(log.getDeviceNames())
                    devices.insert(0, arg)
                    log.setDeviceNames(devices)
                else:
                    cmds.insert(0, LogCommand(arg))
            else:
                raise Exception('Cannot handle scan parameter of type %s' % arg.__class__.__name__)

        seq = CommandSequence(cmds)
        id = self.submit(name, seq)
        if __name__ == '__main__':
            seq.dump()
            self.waitUntilDone()
        return id

# Create 'scan' command
scan = ScanNd()

if __name__ == '__main__':
    print 'Welcome to the scan system'
    # print 'Running in %s' % os.getcwd()
    print 'Connected to %s' % scan.server.getInfo()
    
    # 'Normal' loops
    #scan('Normal 2D', ('xpos', 1, 10), ('ypos', 1, 10, 0.5), 'readback')

    # 'Reversing' inner loop
    #scan('Reversing 2D', ('xpos', 1, 10), ('ypos', 1, 10, -0.5), 'readback')

