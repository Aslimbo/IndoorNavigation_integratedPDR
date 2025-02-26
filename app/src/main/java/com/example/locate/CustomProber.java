package com.example.locate;

import com.hoho.android.usbserial.driver.Ch34xSerialDriver;
import com.hoho.android.usbserial.driver.FtdiSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialProber;

/**
 * Add devices here, that are not known to DefaultProber.
 *
 * If the App should auto-start for these devices, also
 * add IDs to app/src/main/res/xml/device_filter.xml.
 */
class CustomProber {

    static UsbSerialProber getCustomProber() {
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(0x1234, 0x0001, FtdiSerialDriver.class); // Example: device with custom VID+PID
        customTable.addProduct(0x1234, 0x0002, FtdiSerialDriver.class); // Example: device with custom VID+PID
        customTable.addProduct(0x1A86, 0x7522, Ch34xSerialDriver.class); // Example: device with custom VID+PID

        return new UsbSerialProber(customTable);
    }

}