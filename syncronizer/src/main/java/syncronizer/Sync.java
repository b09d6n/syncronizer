package syncronizer;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
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
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

public class Sync {
	
	private static Properties prop;
	
	private static Set<String> destinations;
	private static Set<String> sources;
	private static Set<String> searchSources;

	private static String source;
	private static String destination;
	
	private static List<Consumer<BufferedImage>> transformari = new ArrayList<Consumer<BufferedImage>>();
	
	private static BufferedImage logo;
	
	private static Boolean ignoraFisiereleExistente;
	
	private static Dimension boundary;
	
	private static float alphaValue;

	public static void init(String fisierProprietati) {
		prop = new Properties();
		try(FileReader fd = new FileReader(fisierProprietati)) {
			prop.load(fd);
		} catch (IOException e1) {
			System.err.println("Eroare incarcare proprietati " + e1.getMessage());
			System.exit(1);
		}
		
		source = prop.getProperty("SURSA");
		destination = prop.getProperty("DEST");
		ignoraFisiereleExistente = Boolean.valueOf( prop.getProperty("INIT"));
		boundary = new Dimension(Integer.valueOf(prop.getProperty("WIDTH")), Integer.valueOf(prop.getProperty("HEIGHT")));
		alphaValue =  Float.valueOf( prop.getProperty("ALPHA"));

		List<String> numeFisiere = Arrays.asList(new File(source).list());
		sources = new HashSet<String>(numeFisiere);
		searchSources = new HashSet<String>(numeFisiere.parallelStream().map(String::toUpperCase).collect(Collectors.toList()));
		destinations = new HashSet<String>(Arrays.asList(new File(destination).list()));
		try {
			logo = ImageIO.read(new File(prop.getProperty("LOGO")));
		} catch (IOException e) {
			System.err.println("Eroare incarcare logo " + prop.getProperty("LOGO") + " " + e.getMessage());
			System.exit(1);
		}
		transformari.add(s->resize(s));
		transformari.add(s->puneLogo(s));

		sources.parallelStream().filter(Sync::doCopy).filter(Sync::copiaza).forEach(s->applyTransformations(s));
		destinations.parallelStream().filter(not(searchSources::contains)).forEach(Sync::delete);

	}
	
	private static <T> Predicate<T> not (Predicate<T> p){
		return p.negate();
	}
	
	
	private static Boolean doCopy(String name)  {
		return ignoraFisiereleExistente || (name.toUpperCase().endsWith("JPG") || name.toUpperCase().endsWith("JPEG")) && (!destinations.contains(name.toUpperCase()) || maiNou(name));
	}
	
	private static Boolean maiNou(String name) {
		FileTime sourceTime;
		FileTime destinationTime;
		try {
			sourceTime = Files.getLastModifiedTime(Paths.get(source,name), LinkOption.NOFOLLOW_LINKS);
		
			destinationTime = Files.getLastModifiedTime(Paths.get(destination,name.toUpperCase()), LinkOption.NOFOLLOW_LINKS);
		} catch (IOException e) {
			System.err.println("Eroare citire Data ultimei modificari, fisier " + e.getMessage());
			return false;
		}
		return sourceTime.compareTo(destinationTime)>0;
	}
	private static Boolean copiaza(String nume) {
		try {
			Files.copy(Paths.get(source, nume), Paths.get(destination, nume.toUpperCase()),StandardCopyOption.REPLACE_EXISTING );
			return true;
		} catch (IOException e) {
			System.err.println("Eroare la copierea, fisier " + e.getMessage());
			return false;
		}
	}
	private static void delete(String nume) {
		System.out.println("Sterg " + nume);
		try {
			Files.delete(Paths.get(destination, nume));
		} catch (IOException e) {
			System.err.println("Eroare la stergere, fisier " + e.getMessage());
		}
	}
	private static void applyTransformations(String name) {
		File imaginea = new File(destination,name.toUpperCase());
		BufferedImage img;
		try {
			img = ImageIO.read(imaginea);
			if (img==null) {
				System.err.println("Eroare la deschidere, fisier " + imaginea);
			}else {
				img = resize(img);
				puneLogo(img);
				ImageIO.write(img, "JPG", imaginea );
			}
		} catch (IOException | IllegalArgumentException e) {
			System.err.printf("Eroare la fisierul %s, eroarea este: %s",name,e.getMessage());
			
			return;
		}
	}
	
	private static void puneLogo(BufferedImage img) {
		
		AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alphaValue );
		Graphics2D g2d = (Graphics2D) img.getGraphics();
		
		g2d.setComposite(alphaChannel);
 
        // calculates the coordinate where the image is painted
        int topLeftX = (img.getWidth() - logo.getWidth()) / 2;
        int topLeftY = (img.getHeight() - logo.getHeight()) / 2;
 
        // paints the image watermark
        g2d.drawImage(logo, topLeftX, topLeftY, null);
        
        g2d.setComposite(alphaChannel.derive(1f));
        
        topLeftX = img.getWidth() - (int) (logo.getWidth()* 1.3);
        topLeftY = img.getHeight() - (int) (logo.getHeight()* 1.3);
 
        // paints the image watermark
        g2d.drawImage(logo, topLeftX, topLeftY, null);
        
        g2d.dispose();
	}
	private static BufferedImage resize (BufferedImage img) {		
		
		Dimension imgSize = new Dimension(img.getWidth(), img.getHeight());
		
		Dimension newD = getScaledDimension(imgSize, boundary);
        BufferedImage resizeImageJpg = resizeImage(img, BufferedImage.TYPE_INT_RGB, newD.width, newD.height);
        return resizeImageJpg;
	}
	static Dimension getScaledDimension(Dimension imageSize, Dimension boundary) {

	    double widthRatio = boundary.getWidth() / imageSize.getWidth();
	    double heightRatio = boundary.getHeight() / imageSize.getHeight();
	    double ratio = Math.min(widthRatio, heightRatio);

	    return new Dimension((int) (imageSize.width  * ratio),
	                         (int) (imageSize.height * ratio));
	}
	private static BufferedImage resizeImage(BufferedImage originalImage, int type,
            Integer img_width, Integer img_height)
		{
			BufferedImage resizedImage = new BufferedImage(img_width, img_height, type);
			Graphics2D g = resizedImage.createGraphics();
			g.drawImage(originalImage, 0, 0, img_width, img_height, null);
			g.dispose();
			
			return resizedImage;
		}	
	
	
}
