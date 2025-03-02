/*
 * SVGRenderer.java
 * Copyright (c) 2005-2013 Radek Burget
 *
 * CSSBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * CSSBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *  
 * You should have received a copy of the GNU Lesser General Public License
 * along with CSSBox. If not, see <http://www.gnu.org/licenses/>.
 *
 * Created on 8.3.2013, 14:16:05 by burgetr
 */
package org.fit.cssbox.render;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import javax.imageio.ImageIO;

import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.TermColor;

import org.fit.cssbox.css.CSSUnits;
import org.fit.cssbox.layout.BackgroundImage;
import org.fit.cssbox.layout.BlockBox;
import org.fit.cssbox.layout.Box;
import org.fit.cssbox.layout.ElementBox;
import org.fit.cssbox.layout.LengthSet;
import org.fit.cssbox.layout.ListItemBox;
import org.fit.cssbox.layout.ReplacedBox;
import org.fit.cssbox.layout.ReplacedContent;
import org.fit.cssbox.layout.ReplacedImage;
import org.fit.cssbox.layout.ReplacedText;
import org.fit.cssbox.layout.TextBox;
import org.fit.cssbox.layout.Viewport;
import org.fit.cssbox.layout.VisualContext;
import org.fit.cssbox.misc.Base64Coder;

/**
 * A renderer that produces an SVG output.
 * 
 * @author burgetr
 */
public class SVGRenderer implements BoxRenderer
{
    private PrintWriter out;
    
    private int rootw;
    private int rooth;
    
    private int idcounter;
    
    public SVGRenderer(int rootWidth, int rootHeight, Writer out)
    {
        idcounter = 1;
        rootw = rootWidth;
        rooth = rootHeight;
        this.out = new PrintWriter(out);
        writeHeader();
    }
    
    //====================================================================================================
    
    public void startElementContents(ElementBox elem)
    {
        if (elem instanceof BlockBox && ((BlockBox) elem).getOverflowX() != BlockBox.OVERFLOW_VISIBLE)
        {
            //for blocks with overflow != visible generate a clipping group
            Rectangle cb = elem.getClippedContentBounds();
            String clip = "cssbox-clip-" + idcounter;
            out.print("<clipPath id=\"" + clip + "\">");
            out.print("<rect x=\"" + cb.x + "\" y=\"" + cb.y + "\" width=\"" + cb.width + "\" height=\"" + cb.height + "\" />");
            out.println("</clipPath>");
            out.println("<g id=\"cssbox-obj-" + idcounter + "\" clip-path=\"url(#" + clip + ")\">");
            idcounter++;
        }
    }

    public void finishElementContents(ElementBox elem)
    {
        if (elem instanceof BlockBox && ((BlockBox) elem).getOverflowX() != BlockBox.OVERFLOW_VISIBLE)
        {
            //for blocks with overflow != visible finish the clipping group
            out.println("</g>");
        }
    }

    public void renderElementBackground(ElementBox eb)
    {
        Rectangle bb = eb.getAbsoluteBorderBounds();
        if (eb instanceof Viewport)
            bb = eb.getClippedBounds(); //for the root box (Viewport), use the whole clipped content
        Color bg = eb.getBgcolor();

        //background color
        if (bg != null)
        {
            String style = "stroke:none;fill-opacity:1;fill:" + colorString(bg);
            out.println("<rect x=\"" + bb.x + "\" y=\"" + bb.y + "\" width=\"" + bb.width + "\" height=\"" + bb.height + "\" style=\"" + style + "\" />");
        }
        
        //background image
        if (eb.getBackgroundImages() != null && eb.getBackgroundImages().size() > 0)
        {
            for (BackgroundImage bimg : eb.getBackgroundImages())
            {
                BufferedImage img = bimg.getBufferedImage();
                if (img != null)
                {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    try
                    {
                        ImageIO.write(img, "png", os);
                    } catch (IOException e) {
                        out.println("<!-- I/O error: " + e.getMessage() + " -->");
                    }
                    char[] data = Base64Coder.encode(os.toByteArray());
                    String imgdata = "data:image/png;base64," + new String(data);
                    int ix = bb.x + eb.getBorder().left;
                    int iy = bb.y + eb.getBorder().top;
                    int iw = bb.width - eb.getBorder().right - eb.getBorder().left;
                    int ih = bb.height - eb.getBorder().bottom - eb.getBorder().top;
                    out.println("<image x=\"" + ix + "\" y=\"" + iy + "\" width=\"" + iw + "\" height=\"" + ih + "\" xlink:href=\"" + imgdata + "\" />");
                }
            }
            
        }
    
        //border
        LengthSet borders = eb.getBorder();
        if (borders.top > 0)
            writeBorderSVG(eb, bb.x, bb.y, bb.x + bb.width, bb.y, "top", borders.top, 0, borders.top/2);
        if (borders.right > 0)
            writeBorderSVG(eb, bb.x + bb.width, bb.y, bb.x + bb.width, bb.y + bb.height, "right", borders.right, -borders.right/2, 0);
        if (borders.bottom > 0)
            writeBorderSVG(eb, bb.x, bb.y + bb.height, bb.x + bb.width, bb.y + bb.height, "bottom", borders.bottom, 0, -borders.bottom/2);
        if (borders.left > 0)
            writeBorderSVG(eb, bb.x, bb.y, bb.x, bb.y + bb.height, "left", borders.left, borders.left/2, 0);
    }

    @Override
    public void renderMarker(ListItemBox elem)
    {
        if (elem.getMarkerImage() != null)
        {
            if (!writeMarkerImage(elem))
                writeBullet(elem);
        }
        else
            writeBullet(elem);
    }

    public void renderTextContent(TextBox text)
    {
        Rectangle b = text.getAbsoluteBounds();
        String style = textStyle(text.getVisualContext());
        if (text.getWordSpacing() == null && text.getExtraWidth() == 0) {
            final int[][] offsets = text.getWordOffsets(new String[] {text.getText()});
            writeText(b.x + offsets[0][0], b.y + text.getBaselineOffset(), offsets[0][1], b.height, style, text.getText());
        }
        else
            writeTextByWords(b.x, b.y + text.getBaselineOffset(), b.width, b.height, style, text);
        out.println();
    }

    public void renderReplacedContent(ReplacedBox box)
    {
        ReplacedContent cont = box.getContentObj();
        if (cont != null)
        {
            if (cont instanceof ReplacedImage) //images
            {
                BufferedImage img = ((ReplacedImage) cont).getBufferedImage();
                if (img != null)
                {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    try
                    {
                        ImageIO.write(img, "png", os);
                    } catch (IOException e) {
                        out.println("<!-- I/O error: " + e.getMessage() + " -->");
                    }
                    char[] data = Base64Coder.encode(os.toByteArray());
                    String imgdata = "data:image/png;base64," + new String(data);
                    Rectangle cb = ((Box) box).getAbsoluteContentBounds();
                    out.println("<image x=\"" + cb.x + "\" y=\"" + cb.y + "\" width=\"" + cb.width + "\" height=\"" + cb.height + "\" xlink:href=\"" + imgdata + "\" />");
                }
            }
            else if (cont instanceof ReplacedText) //HTML object
            {
                //clipping rectangle for the object
                Rectangle cb = ((Box) box).getClippedBounds();
                String clip = "cssbox-clip-" + idcounter;
                out.print("<clipPath id=\"" + clip + "\">");
                out.print("<rect x=\"" + cb.x + "\" y=\"" + cb.y + "\" width=\"" + cb.width + "\" height=\"" + cb.height + "\" />");
                out.println("</clipPath>");
                //the group containing the rendered object
                out.println("<g id=\"cssbox-obj-" + (idcounter++) + "\" clip-path=\"url(#" + clip + ")\">");
                ReplacedText rt = (ReplacedText) cont;
                rt.getContentViewport().draw(this);
                out.println("</g>");
            }
        }
    }
    
    public void close()
    {
        writeFooter();
    }
    
    //====================================================================================================
    
    private void writeHeader()
    {
        out.println("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>");
        out.println("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 20010904//EN\" \"http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd\">");
        out.println("<!-- Rendered by CSSBox http://cssbox.sourceforge.net -->");
        out.println("<svg xmlns=\"http://www.w3.org/2000/svg\"");
        out.println("     xmlns:xlink=\"http://www.w3.org/1999/xlink\" xml:space=\"preserve\"");
        out.println("         width=\"" + rootw + "\" height=\"" + rooth + "px\"");
        out.println("         viewBox=\"0 0 " + rootw + " " + rooth + "\"");
        out.println("         zoomAndPan=\"disable\" >");
    }
    
    private void writeFooter()
    {
        out.println("</svg>");
    }

    private void writeBorderSVG(ElementBox eb, int x1, int y1, int x2, int y2, String side, int width, int right, int down)
    {
        TermColor tclr = eb.getStyle().getValue(TermColor.class, "border-"+side+"-color");
        CSSProperty.BorderStyle bst = eb.getStyle().getProperty("border-"+side+"-style");
        if (bst != CSSProperty.BorderStyle.HIDDEN && (tclr == null || !tclr.isTransparent()))
        {
            Color clr = null;
            if (tclr != null)
                clr = CSSUnits.convertColor(tclr.getValue());
            if (clr == null)
            {
                clr = eb.getVisualContext().getColor();
                if (clr == null)
                    clr = Color.BLACK;
            }

            String stroke = "";
            if (bst == CSSProperty.BorderStyle.SOLID)
            {
                stroke = "stroke-width:" + width;
            }
            else if (bst == CSSProperty.BorderStyle.DOTTED)
            {
                stroke = "stroke-width:" + width + ";stroke-dasharray:" + width + "," + width;
            }
            else if (bst == CSSProperty.BorderStyle.DASHED)
            {
                stroke = "stroke-width:" + width + ";stroke-dasharray:" + (3*width) + "," + width;
            }
            else if (bst == CSSProperty.BorderStyle.DOUBLE)
            {
                //double is not supported yet, we'll use single
                stroke = "stroke-width:" + width;
            }
            else //default or unsupported - draw a solid line
            {
                stroke = "stroke-width:" + width;
            }
            
            String coords = "M " + (x1+right) + "," + (y1+down) + " L " + (x2+right) + "," + (y2+down);
            String style = "fill:none;stroke:" + colorString(clr) + ";" + stroke;
            out.println("<path style=\"" + style + "\" d=\"" + coords + "\" />");  
        }
    }
    
    private void writeText(int x, int y, String style, String text)
    {
        out.print("<text x=\"" + x + "\" y=\"" + y + "\" style=\"" + style + "\">" + htmlEntities(text) + "</text>");
    }
    
    private void writeText(int x, int y, int width, int height, String style, String text)
    {
        out.print("<text x=\"" + x + "\" y=\"" + y + "\" width=\"" + width + "\" height=\"" + height + "\" style=\"" + style + "\">" + htmlEntities(text) + "</text>");
    }
    
    private void writeTextByWords(int x, int y, int width, int height, String style, TextBox text)
    {
        String[] words = text.getText().split(" ");
        if (words.length > 0)
        {
            out.print("<g>");
            final int[][] offsets = text.getWordOffsets(words);
            for (int i = 0; i < words.length; i++)
                writeText(x + offsets[i][0], y, offsets[i][1], height, style, words[i]);
            out.print("</g>");
        }
        else {
            final int[][] offsets = text.getWordOffsets(new String[] {text.getText()});
            writeText(x + offsets[0][0], y, offsets[0][1], height, style, text.getText());
        }
    }
    
    private void writeBullet(ListItemBox lb)
    {
        if (lb.hasVisibleBullet())
        {
            VisualContext ctx = lb.getVisualContext();
            int x = (int) Math.round(lb.getAbsoluteContentX() - 1.2 * ctx.getEm());
            int y = (int) Math.round(lb.getAbsoluteContentY() + 0.5 * ctx.getEm());
            int r = (int) Math.round(0.4 * ctx.getEm());
            
            String tclr = colorString(ctx.getColor());
            String style = "";
            
            switch (lb.getListStyleType())
            {
                case "circle":
                    style = "fill:none;fill-opacity:1;stroke:" + tclr + ";stroke-width:1;stroke-miterlimit:4;stroke-dasharray:none;stroke-dashoffset:0;stroke-opacity:1";
                    out.println("<circle style=\"" + style + "\" cx=\"" + (x + r / 2) + "\" cy=\"" + (y + r / 2) + "\" r=\"" + (r / 2) + "\" />");
                    break;
                case "square":
                    style = "fill:" + tclr +";fill-opacity:1;stroke:" + tclr + ";stroke-width:1;stroke-miterlimit:4;stroke-dasharray:none;stroke-dashoffset:0;stroke-opacity:1";
                    out.println("<rect style=\"" + style + "\" x=\"" + x + "\" y=\"" + y + "\" width=\"" + r + "\" height=\"" + r + "\" />");
                    break;
                case "disc":
                    style = "fill:" + tclr +";fill-opacity:1;stroke:" + tclr + ";stroke-width:1;stroke-miterlimit:4;stroke-dasharray:none;stroke-dashoffset:0;stroke-opacity:1";
                    out.println("<circle style=\"" + style + "\" cx=\"" + (x + r / 2) + "\" cy=\"" + (y + r / 2) + "\" r=\"" + (r / 2) + "\" />");
                    break;
                default:
                    
                    int baseline = lb.getFirstInlineBoxBaseline();
                    if (baseline == -1)
                        baseline = ctx.getBaselineOffset(); //use the font baseline
                    style = textStyle(ctx) + ";text-align:end;text-anchor:end";
                    writeText((int) Math.round(lb.getAbsoluteContentX() - 0.5 * ctx.getEm()), lb.getAbsoluteContentY() + baseline, style, lb.getMarkerText());
                    break;
            }
        }
    }
    
    private boolean writeMarkerImage(ListItemBox lb)
    {
        VisualContext ctx = lb.getVisualContext();
        int ofs = lb.getFirstInlineBoxBaseline();
        if (ofs == -1)
            ofs = ctx.getBaselineOffset(); //use the font baseline
        int x = (int) Math.round(lb.getAbsoluteContentX() - 0.5 * ctx.getEm());
        int y = lb.getAbsoluteContentY() + ofs;
        BufferedImage img = lb.getMarkerImage().getBufferedImage();
        if (img != null)
        {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try
            {
                ImageIO.write(img, "png", os);
            } catch (IOException e) {
                out.println("<!-- I/O error: " + e.getMessage() + " -->");
            }
            char[] data = Base64Coder.encode(os.toByteArray());
            String imgdata = "data:image/png;base64," + new String(data);
            int iw = img.getWidth();
            int ih = img.getHeight();
            int ix = x - iw;
            int iy = y - ih;
            out.println("<image x=\"" + ix + "\" y=\"" + iy + "\" width=\"" + iw + "\" height=\"" + ih + "\" xlink:href=\"" + imgdata + "\" />");
            return true;
        }
        else
            return false;
    }
    
    private String textStyle(VisualContext ctx)
    {
        String style = "font-size:" + ctx.getFontSize() + "pt;" + 
                       "font-weight:" + (ctx.getFont().isBold()?"bold":"normal") + ";" + 
                       "font-style:" + (ctx.getFont().isItalic()?"italic":"normal") + ";" +
                       "font-family:" + ctx.getFont().getFamily() + ";" +
                       "fill:" + colorString(ctx.getColor()) + ";" +
                       "stroke:none";
        if (!ctx.getTextDecoration().isEmpty())
            style += ";text-decoration:" + ctx.getTextDecorationString();
        if (ctx.getLetterSpacing() > 0.0001)
            style += ";letter-spacing:" + ctx.getLetterSpacing() + "px";
        return style;
    }

    private String colorString(Color color)
    {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    
    private String htmlEntities(String s)
    {
        return s.replaceAll("&", "&amp;").replaceAll(">", "&gt;").replaceAll("<", "&lt;");
    }
    
}
