package me.deprilula28.gamesrob.commands;

import com.sun.javafx.iio.ImageStorage;
import me.deprilula28.gamesrob.games.Quiz;
import me.deprilula28.gamesrob.utility.GifSequenceWriter;
import me.deprilula28.gamesrob.utility.Interpolation;
import me.deprilula28.gamesrob.utility.Log;
import me.deprilula28.gamesrob.utility.Utility;
import me.deprilula28.jdacmdframework.CommandContext;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.Buffer;
import java.util.concurrent.TimeUnit;

public class Gengif {
    public static String gen(CommandContext context) {
        Log.wrapException("Image build error", () -> {
            /*
            final long timeMs = TimeUnit.SECONDS.toMillis(2L);
            final int fps = 40;
            final long frames = fps * (timeMs / 1000);
            final int frameTime = (int) (timeMs / frames);

            final int type = BufferedImage.TYPE_INT_ARGB;
            final int width = 512;
            final int height = 512;

            final int magWidth = 368;
            final int magHeight = 368;

            Log.info("Rendering ", frames + " @" + fps + " (" + frameTime + "ms)");
            BufferedImage image = ImageIO.read(new File("1f50d.png"));
            ImageOutputStream stream = new FileImageOutputStream(new File("output"));
            GifSequenceWriter writer = new GifSequenceWriter(stream, type, frameTime, true);

            for (int i = 0; i < frames; i ++) {
                BufferedImage frame = new BufferedImage(width, height, type);
                Graphics2D g2d = frame.createGraphics();

                g2d.setComposite(AlphaComposite.Src);

                long half = frames / 2;
                long amount = i > half ? i - half : i;
                float anim = ((float) amount / (float) frames) * 2;
                double progress = Interpolation.pow5.apply(i > half ? 1 - anim : anim);

                final int wobbleMid = 10;
                g2d.drawImage(image, (width - magWidth) / 2, (height - magHeight) / 2,
                        magWidth, magHeight, new Color(0, 0, 0, 0), null);

                AffineTransform tx = new AffineTransform();
                final int from = 0;
                final int to = 35;
                tx.rotate(Math.toRadians(from - progress * (to - from)), width / 2, height / 2);
                tx.translate((int) (progress * wobbleMid) - (wobbleMid / 2), (int) (progress * wobbleMid) - (wobbleMid / 2));

                AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
                frame = op.filter(frame, null);

                writer.writeToSequence(frame);
            }

            writer.close();
            stream.close();
            context.getChannel().sendFile(new File("output")).queue();
            *//*
            int amount = 12;
            for (int i = 0; i <= amount; i ++) {
                BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = image.createGraphics();
                BufferedImage read = ImageIO.read(new File("qmgreen.png"));
                double hue = (((double) i / amount));
                Log.info("Hue: " + hue);

                for (int x = 0; x < 128; x ++) {
                    for (int y = 0; y < 128; y ++) {
                        Color color = new Color(read.getRGB(x, y), true);
                        if (color.getAlpha() == 0) continue;
                        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getBlue(), color.getGreen(), null);
                        Log.info("Hue: " + (float) (hsb[0] + hue));
                        image.setRGB(x, y, Color.HSBtoRGB((float) (hsb[0] + hue), hsb[1], hsb[2]));
                    }
                }

                ImageOutputStream stream = new FileImageOutputStream(new File("questioninverted" + i + ".png"));
                ImageIO.write(image, "png", stream);

                context.getChannel().sendFile(new File("question" + i + ".png")).queue();
            }*/
        });
        return null;
    }
}
