package syncronizer;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

public class Sync {
	
	private static Set<String> destinations;
	private static Set<String> sources;

	private static String source;
	private static String destination;
	
	private static List<Consumer<BufferedImage>> transformari = new ArrayList<Consumer<BufferedImage>>();
	
	private static BufferedImage logo;

	public static void init(String[] args) {
		source = args[0];
		destination = args[1];
		sources = new HashSet<String>(Arrays.asList(new File(source).list()));
		destinations = new HashSet<String>(Arrays.asList(new File(destination).list()));
		try {
			logo = ImageIO.read(new File(args[2]));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		transformari.add(s->resize(s));
		transformari.add(s->puneLogo(s));

		sources.parallelStream().filter(Sync::doCopy).filter(Sync::copiaza).forEach(s->applyTransformations(s));
		destinations.parallelStream().filter(not(sources::contains)).forEach(Sync::delete);

	}
	
	private static <T> Predicate<T> not (Predicate<T> p){
		return p.negate();
	}
	
	private static Boolean doCopy(String name)  {
				
		return !destinations.contains(name.toUpperCase()) || maiNou(name);
	}
	
	private static Boolean maiNou(String name) {
		FileTime sourceTime;
		FileTime destinationTime;
		try {
			sourceTime = Files.getLastModifiedTime(Paths.get(source,name), LinkOption.NOFOLLOW_LINKS);
		
			destinationTime = Files.getLastModifiedTime(Paths.get(destination,name.toUpperCase()), LinkOption.NOFOLLOW_LINKS);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return false;
		}
		return sourceTime.compareTo(destinationTime)>0;
	}
	private static Boolean copiaza(String nume) {
		System.out.println("Copiez " + nume);
		try {
			Files.copy(Paths.get(source, nume), Paths.get(destination, nume.toUpperCase()),StandardCopyOption.REPLACE_EXISTING );
			return true;
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return false;
		}
	}
	private static void delete(String nume) {
		System.out.println("Sterg " + nume);
		try {
			Files.delete(Paths.get(destination, nume));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private static void applyTransformations(String name) {
		File imaginea = new File(destination,name.toUpperCase());
		BufferedImage img;
		try {
			img = ImageIO.read(imaginea);
			transformari.forEach(s->s.accept(img));
			ImageIO.write(img, "JPG", imaginea );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void puneLogo(BufferedImage img) {
		AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f);
		Graphics2D g2d = (Graphics2D) img.getGraphics();

		g2d.setComposite(alphaChannel);
 
        // calculates the coordinate where the image is painted
        int topLeftX = (img.getWidth() - logo.getWidth()) / 2;
        int topLeftY = (img.getHeight() - logo.getHeight()) / 2;
 
        // paints the image watermark
        g2d.drawImage(logo, topLeftX, topLeftY, null);
        
        g2d.dispose();
	}
	private static void resize (BufferedImage img) {
		
		BufferedImage resizedImage = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
	    Graphics2D graphics2D = resizedImage.createGraphics();
	    graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	    graphics2D.drawImage(img, 0, 0, 640, 480, null);
	    img = resizedImage;
	    graphics2D.dispose();
	}
	

}
