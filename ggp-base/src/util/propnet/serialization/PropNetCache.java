package util.propnet.serialization;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import sun.misc.BASE64Encoder;
import util.configuration.ProjectConfiguration;
import util.gdl.grammar.Gdl;
import util.gdl.grammar.GdlPool;
import util.gdl.grammar.GdlProposition;
import util.gdl.grammar.GdlTerm;
import util.logging.GamerLogger;
import util.propnet.architecture.Component;
import util.propnet.architecture.PropNet;
import util.propnet.architecture.components.Proposition;
import util.statemachine.Role;

/**
 * PropNetCache provides a mechanism for loading saved propnets based on their
 * known GDL description. Each propnet is saved in a file determined by the MD5
 * hash of the GDL description, along with a copy of the description, so that
 * when loading the PropNetCache can verify that it is getting the network for
 * the right game. Serialized networks are stored on disk, compressed.
 * 
 * This is designed to be used to supplement the existing @PropNetFactory,
 * which can generate a propnet given the game description. For a few games,
 * like checkers and Zhadu, generating the propnet is time-intensive and also
 * memory-intensive, and isn't feasible to do using the fixed-point iteration
 * approach taken in PropNetFactory. However, we'd still like to be able to
 * test propnet-based players on these games. Thus, in these cases, the propnet
 * can be generated by another system, and loaded into the player through the
 * PropNetCache at runtime.
 * 
 * IMPORTANT NOTE: This is not intended for use during real competitions.
 * It does not handle rulesheet obfuscation at all, nor is it designed to,
 * and depending on a pre-built cache of serialized propnets for specific
 * games feels contrary to the spirit of general game playing competitions.
 * 
 * PropNetCache is designed for the following use cases:
 * 
 *     1. Allowing players to load in propnets for games which they would
 *        otherwise not be able to generate propnets for, for experimentation
 *        and classroom competitions.
 * 
 *     2. Allowing players to load in propnets for games quickly, for running
 *        faster unit tests and benchmarks.
 * 
 * USAGE NOTE: When using this class, you may run into stack overflow errors
 * when loading very large networks, unless you increase your program's stack
 * space using the "-Xss20m" command line flag. If you still have problems,
 * increase the number "20" in that flag to something larger. The flag controls
 * how many megabytes of space are allocated to the stack.
 * 
 * @author Sam Schreiber
 */
public class PropNetCache {
    /**
     * getCacheFile computes the filename for the cache file based on
     * the MD5 digest of the GDL description of the game, with a few minor
     * tweaks to make the result into a more reasonable filename. All of the
     * cached propnet files are stored in the "propNetCacheDirectory" that is
     * defined in the ProjectConfiguration.
     */
    private static File getCacheFile(List<Gdl> description) {
        String gdlHash;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for(Gdl gdl : description) {
                md.update(gdl.toString().getBytes());
            }
            gdlHash = new BASE64Encoder().encode(md.digest()).replace("=", "0").replace("/","_").replace("+",".");
        } catch(Exception e) {            
            GamerLogger.logStackTrace("StateMachine", e);
            return null;
        }
        
        String cacheFilename = "propnet_" + gdlHash + ".net";        
        File theCacheFile = new File(ProjectConfiguration.propNetCacheDirectory, cacheFilename);
        return theCacheFile;
    }
    
    @SuppressWarnings("unchecked")
    public static PropNet loadNetworkFromCache(List<Gdl> description) {
        File theCacheFile = getCacheFile(description);
        if(!theCacheFile.exists()) {
            GamerLogger.log("StateMachine", "Could not find propnet in cache.");
            return null;
        }
        
        GamerLogger.log("StateMachine", "Loading propnet from cache file: " + theCacheFile.getName());
        
        // Deserialize the original description and network.
        PropNet theRawNetwork;
        List<Gdl> oldDescription;
        FileInputStream inStream;
        GZIPInputStream zipStream;
        ObjectInputStream objStream;
        try {
           inStream = new FileInputStream(theCacheFile);
           zipStream = new GZIPInputStream(inStream);
           objStream = new ObjectInputStream(zipStream);
           oldDescription = (List<Gdl>)objStream.readObject();
           theRawNetwork = (PropNet)objStream.readObject();
           objStream.close();
        } catch(Exception e) {
           GamerLogger.logStackTrace("StateMachine", e);
           return null;
        }
        
        // Verify that the string representations of the descriptions match.
        if(description.size() != oldDescription.size()){
            GamerLogger.log("StateMachine", "When loading propnet from cache, found description length mismatch.");
            return null;
        }
        for(int i = 0; i < description.size(); i++) {
            if(!description.get(i).toString().equals(oldDescription.get(i).toString())) {
                GamerLogger.log("StateMachine", "When loading propnet from cache, found descriptions differed.");
                return null;
            }
        }
        
        Set<Proposition> toAdd = new HashSet<Proposition>();
        for (Component c : theRawNetwork.getComponents())
        {
        	if (c instanceof Proposition) continue;
        	Proposition dummy = new Proposition(GdlPool.getConstant("anon"));
        	Set<Component> outputs = new HashSet<Component>(c.getOutputs());
        	for (Component out : outputs)
        	{
        		if (out instanceof Proposition) continue;
        		out.removeInput(c);
        		c.removeOutput(out);
        		c.addOutput(dummy);
        		dummy.addInput(c);
        		dummy.addOutput(out);
        		out.addInput(dummy);
        		toAdd.add(dummy);
        	}
        }
        for (Proposition p : toAdd) theRawNetwork.addComponent(p);
                
        // Immerse all of the GDL that we just deserialized.
        GamerLogger.log("StateMachine", "Loaded propnet from cache. Immersing in GDL pool...");        
        for(Component c : theRawNetwork.getComponents()) {
            if(!(c instanceof Proposition))
                continue;
            
            Proposition p = (Proposition)c;
            p.setName((GdlTerm)GdlPool.immerse(p.getName()));
        }
        
        List<Role> immersedRoles = new ArrayList<Role>();
        for(Role r : theRawNetwork.getRoles()) {
            immersedRoles.add(new Role((GdlProposition)GdlPool.immerse(r.getName())));
        }

        return new PropNet(immersedRoles, theRawNetwork.getComponents());
    }
    
    public static void saveNetworkToCache(List<Gdl> description, PropNet theNetwork) {
        File theCacheFile = getCacheFile(description);
        FileOutputStream outStream;
        GZIPOutputStream zipStream;
        ObjectOutputStream objStream;
        try {
           outStream = new FileOutputStream(theCacheFile);
           zipStream = new GZIPOutputStream(outStream);
           objStream = new ObjectOutputStream(zipStream);
           objStream.writeObject(description);
           objStream.writeObject(theNetwork);
           objStream.close();           
        } catch(Exception e) {
           GamerLogger.logStackTrace("StateMachine", e);
        };
    }
}