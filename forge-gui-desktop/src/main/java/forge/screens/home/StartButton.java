package forge.screens.home;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import forge.localinstance.skin.FSkinProp;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinnedButton;
import forge.toolbox.WarmwoodTheme;

@SuppressWarnings("serial")
public class StartButton extends SkinnedButton {
    public StartButton() {
        setOpaque(false);
        setContentAreaFilled(false);
        setBorder((Border)null);
        setBorderPainted(false);
        setRolloverEnabled(true);
        setRolloverIcon(FSkin.getIcon(FSkinProp.IMG_BTN_START_OVER));
        setIcon(FSkin.getIcon(FSkinProp.IMG_BTN_START_UP));
        setPressedIcon(FSkin.getIcon(FSkinProp.IMG_BTN_START_DOWN));
        // Accessible name.
        this.getAccessibleContext().setAccessibleName("Start game");
        addFocusListener(new FocusListener() {
            @Override
            public void focusLost(FocusEvent arg0) {
                setIcon(FSkin.getIcon(FSkinProp.IMG_BTN_START_UP));
            }
            
            @Override
            public void focusGained(FocusEvent arg0) {
                setIcon(FSkin.getIcon(FSkinProp.IMG_BTN_START_OVER));
            }
        });
        
        addActionListener(e -> {
            setEnabled(false);

            // ensure the click action can resolve before we allow the button to be clicked again
            SwingUtilities.invokeLater(() -> setEnabled(true));
        });
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        if (!FSkin.isWarmwood()) {
            super.paintComponent(graphics);
            return;
        }

        final Graphics2D g = (Graphics2D) graphics.create();
        final WarmwoodTheme.ButtonState state;
        if (!isEnabled()) {
            state = WarmwoodTheme.ButtonState.DISABLED;
        }
        else if (getModel().isPressed()) {
            state = WarmwoodTheme.ButtonState.PRESSED;
        }
        else if (getModel().isRollover() || isFocusOwner()) {
            state = WarmwoodTheme.ButtonState.HOVER;
        }
        else {
            state = WarmwoodTheme.ButtonState.NORMAL;
        }
        WarmwoodTheme.paintButton(g, getWidth(), getHeight(), state);

        g.setFont(FSkin.getRelativeBoldFont(24).getBaseFont());
        final FontMetrics metrics = g.getFontMetrics();
        final String label = "Start";
        final int x = (getWidth() - metrics.stringWidth(label)) / 2;
        final int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        g.setColor(WarmwoodTheme.CREVICE);
        g.drawString(label, x + 1, y + 2);
        g.setColor(WarmwoodTheme.TEXT);
        g.drawString(label, x, y);
        g.dispose();
    }
}
