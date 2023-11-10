/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.microsoftopentechnologies.windowsazurestorage.beans;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author arroyc
 */
public class StorageAccountInfoTest {
    
    private StorageAccountInfo st;
    private String stName = "test1";
    private String stKey = "123";
    private String stBlobURL = "http://test1";
    private String stCdnURL = "http://cdn-test1";

    @Before
    public void setUp() {
        st = new StorageAccountInfo(stName, stKey, stBlobURL, stCdnURL);
    }

    /**
     * Test of getStorageAccName method, of class StorageAccountInfo.
     */
    @Test
    public void testGetStorageAccName() {
        System.out.println("getStorageAccName");
        StorageAccountInfo instance = st;
        assertEquals(stName, instance.getStorageAccName());
    }

    /**
     * Test of setStorageAccName method, of class StorageAccountInfo.
     */
    @Test
    public void testSetStorageAccName() {
        System.out.println("setStorageAccName");
        StorageAccountInfo instance = st;
        assertEquals(stName, instance.getStorageAccName());
        instance.setStorageAccName("zzz");
        assertEquals("zzz", instance.getStorageAccName());
    }

    /**
     * Test of getStorageAccountKey method, of class StorageAccountInfo.
     */
    @Test
    public void testGetStorageAccountKey() {
        System.out.println("getStorageAccountKey");
        StorageAccountInfo instance = st;
        assertEquals(stKey, instance.getStorageAccountKey());
    }

    /**
     * Test of setStorageAccountKey method, of class StorageAccountInfo.
     */
    @Test
    public void testSetStorageAccountKey() {
        System.out.println("setStorageAccountKey");
        String storageAccountKey = "999";
        StorageAccountInfo instance = st;
        assertEquals(stKey, instance.getStorageAccountKey());
        instance.setStorageAccountKey(storageAccountKey);
        assertEquals("999", instance.getStorageAccountKey());
    }

    /**
     * Test of getBlobEndPointURL method, of class StorageAccountInfo.
     */
    @Test
    public void testGetBlobEndPointURL() {
        System.out.println("getBlobEndPointURL");
        StorageAccountInfo instance = st;
        assertEquals(stBlobURL, instance.getBlobEndPointURL());
    }

    /**
     * Test of setBlobEndPointURL method, of class StorageAccountInfo.
     */
    @Test
    public void testSetBlobEndPointURL() {
        System.out.println("setBlobEndPointURL");
        StorageAccountInfo instance = st;
        assertEquals(stBlobURL, instance.getBlobEndPointURL());
        instance.setBlobEndPointURL("abcd");
        assertEquals("abcd", instance.getBlobEndPointURL());
    }

    /**
     * Test of getCdnEndPointURL method, of class StorageAccountInfo.
     */
    @Test
    public void testGetCdnEndPointURL() {
        System.out.println("getCdnEndPointURL");
        StorageAccountInfo instance = st;
        assertEquals(stCdnURL, instance.getCdnEndPointURL());
    }

    /**
     * Test of setCdnEndPointURL method, of class StorageAccountInfo.
     */
    @Test
    public void testSetCdnEndPointURL() {
        System.out.println("setCdnEndPointURL");
        StorageAccountInfo instance = st;
        assertEquals(stCdnURL, instance.getCdnEndPointURL());
        instance.setCdnEndPointURL("cdn-123");
        assertEquals("cdn-123", instance.getCdnEndPointURL());
    }
}
