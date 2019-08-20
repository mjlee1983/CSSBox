/*
 * TextBox.java
 * Copyright (c) 2005-2007 Radek Burget
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
 * Created on 5. �nor 2006, 13:42
 */

package org.fit.cssbox.layout;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.text.AttributedString;

import cz.vutbr.web.css.NodeData;
import org.w3c.dom.Text;

import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.CSSProperty.TextTransform;
import cz.vutbr.web.css.CSSProperty.WordSpacing;
import cz.vutbr.web.css.TermLength;
import sun.font.FontDesignMetrics;

/**
 * A box that corresponds to a text node.
 *
 * @author  radek
 */
public class TextBox extends Box implements Inline
{
    /** Assigned text node */
    private Text textNode;
    
    /** Text string after whitespace processing */
    private String text;
    
    /** The start index of the text substring to be displayed */
    private int textStart;

    /** The end index of the text substring to be displayed (excl) */
    private int textEnd;

    /** Maximal total width */
    private int maxwidth;
    
    /** Minimal total width */
    private int minwidth;
    
    /** Additional width required for justifying the text */
    private int expwidth;
    
    /** Expanding at the line end? */
    private boolean expAtLineEnd;
    
    /** Indicates whether to ignore initial whitespaces */
    private boolean ignoreinitialws;
    
    /** Indicates whether to collapse whitespaces at all */
    private boolean collapsews;
    
    /** Indicates whether it is allowed to split on whitespace */
    private boolean splitws;
    
    /** Indicatew whether line feeds should be treated as whitespace */
    private boolean linews;
    
    /** When line feeds are preserved, this contains the maximal length of the first line of the text. */
    private int firstLineLength;
    
    /** When line feeds are preserved, this contains the maximal length of the last line of the text. */
    private int lastLineLength;
    
    /** Contains the maximal length of the longest line of the text. */
    private int longestLineLength;
    
    /** Contains a preserved line break? */
    private boolean containsLineBreak;
    
    /** Layout finished with a line break? */
    private boolean lineBreakStop;
    
    /** Collapsed to an empty box? (e.g. whitespaces only) */
    private boolean collapsedCompletely;
    
    /** Used text transformation */
    private CSSProperty.TextTransform transform;
    
    /** Word spacing */
    private Float wordSpacing;
    
    
    //===================================================================
    public TextBox(Text n, Graphics2D g, VisualContext ctx, NodeData style) {
        super(n, g, ctx);
        textNode = n;
        transform = TextTransform.NONE;
        wordSpacing = null;
        setWhiteSpace(ElementBox.WHITESPACE_NORMAL); //resets the text content and indices
        ctx.updateForGraphics(style, g);

        ignoreinitialws = false;
        collapsews = true;
        containsLineBreak = false;
        lineBreakStop = false;
        collapsedCompletely = false;
    }

    /**
     * Creates a new TextBox formed by a DOM text node.
     * @param n the corresponding DOM text node
     * @param g the graphics context used for rendering
     * @param ctx current visual context
     */
    public TextBox(Text n, Graphics2D g, VisualContext ctx)
    {
        this(n, g, ctx, null);
    }

    /**
     * Copy all the values from another text box.
     * @param src the source text box
     */
    public void copyValues(TextBox src)
    {
        super.copyValues(src);
        text = new String(src.text);
        ignoreinitialws = false; //only the first box should ignore
        collapsews = src.collapsews;
        splitws = src.splitws;
        linews = src.linews;
        firstLineLength = src.firstLineLength;
        lastLineLength = src.lastLineLength;
        longestLineLength = src.longestLineLength;
        containsLineBreak = src.containsLineBreak;
        transform = src.transform;
        wordSpacing = src.wordSpacing;
    }
    
    /** 
     * Create a new box from the same DOM node in the same context
     * @return the new TextBox 
     */
    public TextBox copyTextBox()
    {
        TextBox ret = new TextBox(textNode, g, ctx);
        ret.copyValues(this);
        return ret;
    }
    
    public String toString()
    {
        return "Text: " + text + "<" + textStart + "," + textEnd + ">";
    }
    
    @Override
    public void initBox()
    {
    }
    
    @Override
    public void setParent(ElementBox parent)
    {
        super.setParent(parent);
        if (getParent() != null)
        {
            //load the relevant style values
            transform = getParent().getStyle().getProperty("text-transform");
            if (transform == null)
                transform = TextTransform.NONE;
            CSSProperty.WordSpacing wspacing = getParent().getStyle().getProperty("word-spacing");
            if (wspacing != null && wspacing != WordSpacing.NORMAL)
            {
                TermLength lenspec = getParent().getStyle().getValue(TermLength.class, "word-spacing");
                if (lenspec != null)
                    wordSpacing = (float) ctx.pxLength(lenspec);            
            }
            else
                wordSpacing = null;
            //reset the whitespace processing according to the parent settings
            CSSProperty.WhiteSpace ws = getParent().getWhiteSpace();
            if (ws != ElementBox.WHITESPACE_NORMAL || transform != TextTransform.NONE)
                setWhiteSpace(ws);
        }
    }

    /**
     * @return the text contained in this box
     */
    @Override
    public String getText()
    {
        if (text != null)
            return text.substring(textStart, textEnd);
        else
            return "";
    }

    @Override
    public boolean isDeclaredVisible()
    {
        return true;
    }

    @Override
    public boolean isDisplayed()
    {
        if (getParent() == null)
            return true;
        else
            return parent.isDisplayed();
    }

    @Override
    public boolean isVisible()
    {
        if (getParent() == null)
            return true;
        else
            return parent.isDeclaredVisible() && super.isVisible();
    }
    
    //=======================================================================

    /**
     * Sets the whitespace processing for this box. The text content is then treated accordingly. The text start and text end
     * indices are reset to their initial values.
     */
    public void setWhiteSpace(CSSProperty.WhiteSpace value)
    {
        splitws = (value == ElementBox.WHITESPACE_NORMAL || value == ElementBox.WHITESPACE_PRE_WRAP || value== ElementBox.WHITESPACE_PRE_LINE);
        collapsews = (value == ElementBox.WHITESPACE_NORMAL || value == ElementBox.WHITESPACE_NOWRAP || value == ElementBox.WHITESPACE_PRE_LINE);
        linews = (value == ElementBox.WHITESPACE_NORMAL || value == ElementBox.WHITESPACE_NOWRAP);
        //When this is the original box, apply the whitespace. For the copied boxes, the whitespace has been already applied (they contain
        //a copy of the original, already processed content). 
        if (!splitted)
            applyWhiteSpace();
        //recompute widths (possibly different wrapping)
        computeLineLengths();
        minwidth = computeMinimalWidth();
        maxwidth = computeMaximalWidth();
    }
    
    /**
     * Applies the whitespace processing represented by the {@link #collapsews} property to the text content. The text start and text end
     * indices are reset to their initial values.
     */
    private void applyWhiteSpace()
    {
        text = applyTransformations(collapseWhitespaces(node.getNodeValue()));
        textStart = 0;
        textEnd = text.length();
        isempty = (textEnd == 0);
    }
    
    /**
     * Applies the whitespace removal rules used in HTML.
     * @param src source string
     * @return a new string with additional whitespaces removed
     */
    private String collapseWhitespaces(String src)
    {
        StringBuffer ret = new StringBuffer();
        boolean inws = false;
        for (int i = 0; i < src.length(); i++)
        {
            char ch = src.charAt(i);
            if (collapsews && isWhitespace(ch))
            {
                if (!inws)
                {
                    ret.append(' ');
                    inws = true;
                }
            }
            else if (isLineBreak(ch))
            {
            	ret.append('\r'); //represent all line breaks as LF
            	//reduce eventual CR+LF to CR only
            	if (ch == '\r' && i+1 < src.length() && src.charAt(i+1) == '\n')
            		i++;
            }
            else
            {
                inws = false;
                ret.append(ch);
            }
        }
        return new String(ret);
    }

    /**
     * Applies the text transformations to a string according to the current style.
     * @param src The source string
     * @return the string after transformations 
     */
    private String applyTransformations(String src)
    {
        switch (transform)
        {
            case LOWERCASE:
                return src.toLowerCase();
            case UPPERCASE:
                return src.toUpperCase();
            case CAPITALIZE:
                StringBuilder ret = new StringBuilder(src.length());
                boolean ws = true;
                for (int i = 0; i < src.length(); i++)
                {
                    char ch = src.charAt(i);
                    if (Character.isWhitespace(ch))
                        ws = true;
                    else
                    {
                        if (ws)
                            ch = Character.toUpperCase(ch);
                        ws = false;
                    }
                    ret.append(ch);
                }
                return ret.toString();
            default:
                return src;
        }
    }
    
    /**
     * Removes the trailing whitespaces from the cotnained text string. This must be done before the layout (it resets the text start and end pointers). 
     */
    public void removeTrailingWhitespaces()
    {
        int last = -1;
        for (int i = text.length() - 1; i >= 0; i--)
        {
            if (isWhitespace(text.charAt(i)))
                last = i;
            else
                break;
        }
        if (last != -1)
        {
            text = text.substring(0, last);
            textStart = 0;
            textEnd = last;
        }
    }
    
    /**
     * @return the start offset in the text string
     */ 
    protected int getTextStart()
    {
        return textStart;
    }
    
    /**
     * @param index the start offset in the text string
     */ 
    protected void setTextStart(int index)
    {
        textStart = index;
    }
    
    /**
     * @return the end offset in the text string (not included)
     */ 
    protected int getTextEnd()
    {
        return textEnd;
    }
    
    /**
     * @param index the end offset in the text string (not included)
     */ 
    protected void setTextEnd(int index)
    {
        textEnd = index;
    }
    
	@Override
    public boolean affectsDisplay()
    {
        return !isEmpty();
    }
    
	@Override
    public boolean isWhitespace()
    {
		//after wihtespace processing, all whitespaces should be represented by ' '
		String s = getText();
		for (int i = 0; i < s.length(); i++)
			if (s.charAt(i) != ' ')
				return false;
		return true;
    }
    
    @Override
    public boolean collapsesSpaces()
    {
        return collapsews;
    }
    
    @Override
    public boolean preservesLineBreaks()
    {
        return !linews;
    }

    @Override
    public boolean allowsWrapping()
    {
        return splitws;
    }

    @Override
    public int getContentX() 
    {
        return bounds.x;
    }
    
	@Override
    public int getAbsoluteContentX() 
    {
        return absbounds.x;
    }
    
	@Override
    public int getContentY() 
    {
        return bounds.y;
    }

	@Override
    public int getAbsoluteContentY() 
    {
        return absbounds.y;
    }

	@Override
    public int getContentWidth() 
    {
        return stringWidth(g.getFontMetrics(), this.text);
    }
    
	@Override
    public int getContentHeight() 
    {
        return getTotalLineHeight();
    }

	@Override
    public int getAvailableContentWidth() 
    {
        return availwidth;
    }

    @Override
    public int getLineHeight()
    {
        return parent.getLineHeight();
    }

    @Override
    public int getMaxLineHeight()
    {
        return this.getTotalLineHeight();
    }

    @Override
    public int getTotalLineHeight()
    {
        return stringHeight(g.getFontMetrics(), this.text);
    }

    @Override
    public int getBaselineOffset()
    {
        return ctx.getBaselineOffset();
    }

    @Override
    public int getBelowBaseline()
    {
        return this.getTotalLineHeight() - ctx.getBaselineOffset();
    }

    @Override
    public int getHalfLead()
    {
        return 0;
    }
    
    /**
     * Computes the line metrics for the particular text content and graphical context.
     * @return the line metrics
     */
    public LineMetrics getLineMetrics()
    {
        return ctx.getFont().getLineMetrics(getText(), g.getFontRenderContext());
    }
    
    @Override
    public int totalHeight() { return bounds.height; }
    
	@Override
    public int totalWidth() 
    {
        return bounds.width;
    }
    
	@Override
    public Rectangle getMinimalAbsoluteBounds()
    {
    	return absbounds;
    }
    
	@Override
    public boolean isInFlow()
	{
		return true;
	}
	
	@Override
	public boolean containsFlow()
	{
		return false;
	}
	
    @Override
    public boolean canSplitInside()
    {
        return (getText().indexOf(' ') != -1);
    }
    
    @Override
    public boolean canSplitBefore()
    {
        if (textEnd > textStart)
        	return (text.charAt(textStart) == ' ' ||
        			(textStart > 0 && text.charAt(textStart-1) == ' '));
        else
        	return false;
    }
    
    @Override
    public boolean canSplitAfter()
    {
        if (textEnd > textStart)
	        return (text.charAt(textEnd-1) == ' ' ||
	                (textEnd < text.length() && text.charAt(textEnd) == ' '));
        else
        	return false;
    }
    
    @Override
    public boolean startsWithWhitespace()
    {
        if (textEnd > textStart)
            return (Character.isWhitespace(text.charAt(textStart)));
        else
            return false;
    }
    
    @Override
    public boolean endsWithWhitespace()
    {
        if (textEnd > textStart)
            return (Character.isWhitespace(text.charAt(textEnd-1)));
        else
            return false;
    }
    
    @Override
    public void setIgnoreInitialWhitespace(boolean b)
    {
        this.ignoreinitialws = b;
        //collapse spaces when necessary
        if (ignoreinitialws && collapsews)
        {
            while (textStart < textEnd && isWhitespace(text.charAt(textStart)))
                textStart++;
            if (textStart == textEnd)
                collapsedCompletely = true;
            //recompute widths (possibly different wrapping)
            computeLineLengths();
            minwidth = computeMinimalWidth();
            maxwidth = computeMaximalWidth();
        }
    }
    
	@Override
	public boolean hasFixedWidth()
	{
		return false; //the width depends on the content
	}

	@Override
	public boolean hasFixedHeight()
	{
		return false;
	}

    //=======================================================================
    
	/** 
	 * Compute the width and height of this element. Layout the sub-elements.
     * @param widthlimit Maximal width available for the element
     * @param force Use the area even if the used width is greater than maxwidth
     * @param linestart Indicates whether the element is placed at the line start
     * @return <code>true</code> if the box has been succesfully placed
     */
	@Override
    public boolean doLayout(int widthlimit, boolean force, boolean linestart)
    {
        //Skip if not displayed
        if (!displayed)
        {
            bounds.setSize(0, 0);
            return true;
        }
        
        //reset textEnd if we are doing a new layout
        if (!splitted)
            textEnd = text.length();
        
        setAvailableWidth(widthlimit);
        
        boolean split = false; //should we split to more boxes?
        boolean allow = false; //allow succesfull result even if nothing has been placed (line break only)
        boolean fail = false; //failed totally (nothing fit)
        int wlimit = getAvailableContentWidth();
        boolean empty = isempty;
        FontMetrics fm = g.getFontMetrics();
        int w = 0, h = 0;
        
        int end = textEnd;
        int lineend = text.indexOf('\r', textStart);
        if (lineend != -1 && lineend < end) //preserved end-of-line encountered
        {
        	end = lineend; //split at line end (or earlier)
        	split = true;
        	allow = true;
        }
        
        if (!empty || !linestart) //ignore empty text elements at the begining of a line
        {
            //ignore spaces at the begining of a line
            if ((linestart || ignoreinitialws) && collapsews)
            {
                while (textStart < end && isWhitespace(text.charAt(textStart)))
                    textStart++;
                if (textStart == end)
                {
                    collapsedCompletely = true;
                    empty = true; //collapsed to an empty box
                }
            }
            //try to place the text
            do
            {
                w = stringWidth(fm, text.substring(textStart, end));
                h = stringHeight(fm, text.substring(textStart, end));
                if (w > wlimit) //exceeded - try to split if allowed
                {
                    if (empty) //empty or just spaces - don't place at all
                    {
                        w = 0; h = 0;
                        split = false;
                        break;
                    }
                    int wordend = text.substring(0, end).lastIndexOf(' '); //find previous word
                    while (wordend > 0 && text.charAt(wordend-1) == ' ') wordend--; //skip trailing spaces
                    if (wordend <= textStart || !splitws) //no previous word, cannot split or splitting not allowed
                    {
                        if (!force) //everything failed
                        {
                        	//System.out.println("Here for " + this);
                            end = textEnd; //we will try with the whole rest next time
                            split = false; 
                            allow = false; //split before the linebreak
                            fail = true;
                        }
                        else
                            split = true;
                        break;
                    }
                    else
                    {
                        end = wordend;
                        split = true;
                    }
                }
            } while (end > textStart && w > wlimit);
        }
        textEnd = end;
        bounds.setSize(w, h);
        
        //if not the whole element was placed, create the rest
        if (split)
        {
            //skip the eventual line break
            int start = textEnd;
            if (start < text.length() && isLineBreak(text.charAt(start)))
            {
                start++;
                lineBreakStop = true;
            }
            //find the start of the next word
            if (collapsews)
            {
                while (start < text.length() && isWhitespace(text.charAt(start)))
                    start++;
            }
            //create the rest if something has left
            if (start < text.length())
            {
                TextBox rtext = copyTextBox();
                rtext.splitted = true;
                rtext.splitid = splitid + 1;
                rtext.setTextStart(start);
                rtext.setTextEnd(text.length());
                rest = rtext;
            }
            else
                rest = null;
        }
        else
            rest = null;
        
        return !fail && ((textEnd > textStart) || empty || allow);
    }
    
	@Override
    public void absolutePositions()
    {
	    updateStackingContexts();
        if (displayed)
        {
            //my top left corner
            absbounds.x = getParent().getAbsoluteContentX() + bounds.x;
            absbounds.y = getParent().getAbsoluteContentY() + bounds.y;
	        absbounds.width = bounds.width;
	        absbounds.height = bounds.height;
        }
    }
    
	@Override
    public int getMinimalWidth()
    {
		return minwidth;
    }
	
    private int computeMinimalWidth()
    {
        int ret = 0;
        String t = getText();
        if (t.length() > 0)
        {
            if (splitws)
            {
                //wrapping allowed - returns the length of the longest word
                ret = getLongestWord();
            }
            else
            {
            	//cannot wrap - return the width of the whole string
                ret = longestLineLength;
            }
        }
        return ret;
    }
    
	@Override
    public int getMaximalWidth()
    {
		return computeMaximalWidth();
    }
	
    private int computeMaximalWidth()
    {
        if (linews)
        {
            //no preserved line breaks -- returns the lenth of the whole string
            int len = stringWidth(g.getFontMetrics(), getText());
            firstLineLength = len;
            lastLineLength = len;
            longestLineLength = len;
            return len;
        }
        else
        {
            return longestLineLength;
        }
    }

    private int getLongestWord()
    {
        int ret = 0;
        String t = getText();
        FontMetrics fm = g.getFontMetrics();
        
        int s1 = 0;
        int s2 = t.indexOf(' ');
        do
        {
            if (s2 == -1) s2 = t.length();
            int w = stringWidth(fm, t.substring(s1, s2));
            if (w > ret) ret = w;
            s1 = s2 + 1;
            s2 = t.indexOf(' ', s1);
        } while (s1 < t.length() && s2 < t.length());
        
        return ret;
    }

    @Override
    public int getFirstLineLength()
    {
        return firstLineLength;
    }

    @Override
    public int getLastLineLength()
    {
        return lastLineLength;
    }

    @Override
    public boolean containsLineBreak()
    {
        return containsLineBreak;
    }
    
    @Override
    public boolean finishedByLineBreak()
    {
        return lineBreakStop;
    }

    @Override
    public boolean collapsedCompletely()
    {
        return collapsedCompletely;
    }
    
    @Override
    public int getWidthExpansionPoints(boolean atLineStart, boolean atLineEnd)
    {
        if (collapsews && splitws && !lineBreakStop)
        {
            int cnt = 0;
            final String text = getText();
            for (int i = 0; i < text.length(); i++)
            {
                if (text.charAt(i) == ' '
                        && !(i == 0 && atLineStart) //do not consider the initial space if at line beginning 
                        && !(i == text.length() - 1 && atLineEnd)) //do not consider the trailing space if at line end
                    cnt++;
            }
            return cnt;
        }
        else
            return 0; //spaces preserved or wrapping not allowed - cannot expand
    }

    @Override
    public void extendWidth(int ofs, boolean atLineStart, boolean atLineEnd)
    {
        expwidth = ofs;
        expAtLineEnd = atLineEnd;
        bounds.width += ofs;
    }
    
    /**
     * The extra box width used for justifying the contents. The rendered width should be extended
     * by this amount in total.
     * @return The extra width or 0 when no extra width is required
     */
    public int getExtraWidth()
    {
        return expwidth;
    }

    /**
     * The additional word spacing in pixels.
     * @return the additional word spacing in pixels or <code>null</code> when normal word spacing is used
     */
    public Float getWordSpacing()
    {
        return wordSpacing;
    }

    /**
     * Computes the X offsets of the individual words
     * @param words the words
     * @return the array n elements where n is the number of words. The a[i][0] contains the
     * offset of the i-th words, a[i][1] contains the length of the word.
     */
    public int[][] getWordOffsets(String[] words)
    {
        return getWordOffsets(g.getFontMetrics(), words);
    }
    
    private int[][] getWordOffsets(FontMetrics fm, String[] words)
    {
        if (words.length > 0)
        {
            //determine word lengths
            int[] ww = new int[words.length];
            int totalw = 0;
            for (int i = 0; i < words.length; i++)
            {
                ww[i] = stringWidth(fm, words[i]);
                totalw += ww[i];
            }
            //spacing
            int spaces = words.length - 1;
            if (startsWithWhitespace())
                spaces++;
            if (endsWithWhitespace() && !expAtLineEnd)
                spaces++;
            final float spacing = (spaces == 0) ? (bounds.width - totalw) : (bounds.width - totalw) / (float) spaces;
            //layout
            int[][] ret = new int[words.length][2];
            float curX = 0;
            if (startsWithWhitespace())
                curX += spacing;
            for (int i = 0; i < words.length; i++)
            {
                ret[i][0] = Math.round(curX);
                ret[i][1] = ww[i];
                curX += ww[i] + spacing;            
            }
            return ret;
        }
        else
            return new int[0][0];
    }
    
    
    
    /**
     * Computes the X pixel offset of the given character of the box text. The character position is specified relatively
     * to the box text.
     * @param pos the character position in the string relative to the start of the box (0 is the first character in the box)
     * @return the X offset in pixels
     */
    public int getCharOffsetX(int pos)
    {
        return getCharOffsetXElem(pos + textStart);
    }
    
    /**
     * Computes the X pixel offset of the given character of the box text. The character position is specified within the
     * whole source text node.
     * @param pos the character position in the string absolutely within the source text node (0 is the first character in the node)
     * @return the X offset in pixels or 0 when the position does not fit to this box
     */
    public int getCharOffsetXElem(int pos)
    {
        if (text != null)
        {
            FontMetrics fm = g.getFontMetrics();
            if (pos <= textStart)
                return 0;
            else if (pos > textStart && pos < textEnd)
                return stringWidth(fm, text.substring(textStart, pos));
            else
                return stringWidth(fm, text.substring(textStart, textEnd));
        }
        else
            return 0;
    }
    
    /**
     * Computes the lengths of the first, last and longest lines.
     */
    protected void computeLineLengths()
    {
        firstLineLength = -1;
        lastLineLength = 0;
        longestLineLength = 0;
        
        String t = getText();
        FontMetrics fm = g.getFontMetrics();
        
        int s1 = 0;
        int s2 = t.indexOf('\r');
        int w = 0;
        do
        {
            if (s2 == -1)
                s2 = t.length();
            else
                containsLineBreak = true;
            w = stringWidth(fm, t.substring(s1, s2));
            if (firstLineLength == -1) firstLineLength = w;
            if (w > longestLineLength) longestLineLength = w;
            s1 = s2 + 1;
            s2 = t.indexOf('\r', s1);
        } while (s1 < t.length() && s2 < t.length());
        lastLineLength = w;
    }

    /**
     * Computes the final width of a string while considering word-spacing
     * @param fm the font metrics used for calculation
     * @param givenText the string to be measured
     * @return the resulting width in pixels
     */
    private int stringWidth(FontMetrics fm, String givenText)
    {
        int w = 0;
        char[] text = givenText.toCharArray();
        for (int idx = 0; idx < text.length; idx++)
        {
            boolean matchFound = false;
            int idxStart = idx;
            int codePoint = Character.codePointAt(text, idx);
            int idxEnd = idxStart + Character.charCount(codePoint);
            idx = idxEnd - 1;
            for (int i = 0; i < ctx.getFallbackFonts().size(); i++) {
                Font font = ctx.getFallbackFonts().get(i);
                if (font.canDisplay(codePoint)) {
                    matchFound = true;
                    FontMetrics fontMetrics = FontDesignMetrics.getMetrics(font, g.getFontRenderContext());
                    w += fontMetrics.stringWidth(new String(Character.toChars(codePoint)));
                    break;
                }
            }
            if (!matchFound) {
                w += fm.stringWidth(new String(Character.toChars(codePoint)));
            }
        }
        if (wordSpacing != null)
        {
            //count spaces and add
            float add = 0.0f;
            for (int i = 0; i < givenText.length(); i++)
            {
                if (givenText.charAt(i) == ' ')
                    add += wordSpacing;
            }
            w = Math.round(w + add);
        }
        return w;
    }

    private int stringHeight(FontMetrics fm, String givenText) {
        int height = fm.getHeight();
        char[] text = givenText.toCharArray();
        for (int idx = 0; idx < text.length; idx++)
        {
            int idxStart = idx;
            int codePoint = Character.codePointAt(text, idx);
            int idxEnd = idxStart + Character.charCount(codePoint);
            idx = idxEnd - 1;
            for (int i = 0; i < ctx.getFallbackFonts().size(); i++) {
                Font font = ctx.getFallbackFonts().get(i);
                if (font.canDisplay(codePoint)) {
                    FontMetrics fontMetrics = FontDesignMetrics.getMetrics(font, g.getFontRenderContext());
                    height = Math.max(height, fontMetrics.getHeight());
                    break;
                }
            }
        }
        return height;
    }
    
    /** 
     * Draw the text content of this box (no subboxes)
     * @param g the graphics context to draw on
     */
    public void drawContent(Graphics2D g)
    {
        //top left corner
        int x = absbounds.x;
        int y = absbounds.y;

        //Draw the string
        if (textEnd > textStart)
        {
            String t = text.substring(textStart, textEnd);
            Shape oldclip = g.getClip();
            if (clipblock != null)
                g.setClip(applyClip(oldclip, clipblock.getClippedContentBounds()));
            ctx.updateGraphics(g);
            
            if (wordSpacing == null && expwidth == 0)
                drawAttributedString(g, x, y, t);
            else
                drawByWords(g, x, y, t);
            g.setClip(oldclip);
        }
    }

    private int drawByWords(Graphics2D g, int x, int y, String text)
    {
        int offset = 0;
        String[] words = text.split(" ");
        if (words.length > 0)
        {
            for (int i = 0; i < words.length; i++)
                offset += drawAttributedString(g, x + offset, y, words[i]);
        }
        else
            offset += drawAttributedString(g, x, y, text);
        return offset;
    }
    
    /**
     * Draws a single string with eventual attributes based on the current visual context.
     */
    private int drawAttributedString(Graphics2D g, int x, int y, String givenText)
    {
        char[] text = givenText.toCharArray();
        int offset = 0;
        int ascent = getBaselineOffset();
        AttributedString as = new AttributedString(givenText);
        if (!ctx.getTextDecoration().isEmpty())
        {
            if (ctx.getTextDecoration().contains(CSSProperty.TextDecoration.UNDERLINE))
                as.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            if (ctx.getTextDecoration().contains(CSSProperty.TextDecoration.LINE_THROUGH))
                as.addAttribute(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
        }
        for (int idx = 0; idx < text.length; idx++)
        {
            boolean matchFound = false;
            int idxStart = idx;
            int codePoint = Character.codePointAt(text, idx);
            int idxEnd = idxStart + Character.charCount(codePoint);
            idx = idxEnd - 1;
            for (int i = 0; i < ctx.getFallbackFonts().size(); i++) {
                Font font = ctx.getFallbackFonts().get(i);
                if (font.canDisplay(codePoint)) {
                    as.addAttribute(TextAttribute.FONT, font, idxStart, idxEnd);
                    matchFound = true;
                    FontMetrics fontMetrics = FontDesignMetrics.getMetrics(font, g.getFontRenderContext());
                    int[][] letterOffset = getWordOffsets(fontMetrics, new String[] {new String(Character.toChars(codePoint))});
                    offset += letterOffset[0][0];
                    ascent = Math.max(ascent, fontMetrics.getAscent());
                    break;
                }
            }
            if (!matchFound) {
                as.addAttribute(TextAttribute.FONT, ctx.getFont(), idxStart, idxEnd);
            }
        }
        g.drawString(as.getIterator(), x, y + ascent);
        return offset;
    }
    
	@Override
    public void draw(DrawStage turn)
    {
        if (displayed && isVisible())
        {
            if (turn == DrawStage.DRAW_INLINE)
            {
                getViewport().getRenderer().renderTextContent(this);
            }
        }
    }
    
	@Override
    public void drawExtent(Graphics2D g)
    {
        //draw the full box
        g.setColor(Color.ORANGE);
        g.drawRect(absbounds.x, absbounds.y, bounds.width, bounds.height);
        /*for (int i = 0; i < getText().length(); i++)
        {
            if (i != 0) System.out.print(" : ");
            char ch = getText().charAt(i);
            System.out.print(ch);
            System.out.print((int) ch);
            if (Character.isWhitespace(ch))
                System.out.print("!");
            if (Character.isSpaceChar(ch))
                System.out.print("*");
        }
        System.out.println();*/
        
        g.setColor(Color.MAGENTA);
        int y = getAbsoluteContentY();
        int h = getTotalLineHeight();
            
        g.drawRect(getAbsoluteContentX(), y, getContentWidth(), h);
        
        g.setColor(Color.BLUE);
        y = getAbsoluteContentY() + getBaselineOffset();
        g.drawRect(getAbsoluteContentX(), y, getContentWidth(), 1);
        
        
    }
	
	//===============================================================================
	
	/**
	 * Checks if a character can be interpreted as whitespace according to current settings.
	 * @param ch the character
	 * @return <code>true</code> when <code>ch</code> is a whitespace character
	 */
    private boolean isWhitespace(char ch)
    {
        if (linews) 
            return Character.isWhitespace(ch);
        else
            return ch != '\n' && ch != '\r' && Character.isWhitespace(ch);
    }
    
    /**
     * Checks if a character can be interpreted as a line break according to current settings.
     * @param ch the character
     * @return <code>true</code> when <code>ch</code> is the line break
     */
    private boolean isLineBreak(char ch)
    {
        if (linews)
            return false;
        else
            return (ch == '\r' || ch == '\n');
    }
    

}
