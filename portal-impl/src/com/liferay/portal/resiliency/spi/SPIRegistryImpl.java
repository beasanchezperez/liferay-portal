/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.resiliency.spi;

import com.liferay.portal.kernel.concurrent.ConcurrentHashSet;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.resiliency.spi.SPI;
import com.liferay.portal.kernel.resiliency.spi.SPIConfiguration;
import com.liferay.portal.kernel.resiliency.spi.SPIRegistry;
import com.liferay.portal.model.Portlet;
import com.liferay.portal.model.PortletApp;
import com.liferay.portal.service.PortletLocalServiceUtil;
import com.liferay.portal.spring.aop.ServiceBeanAopCacheManagerUtil;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Shuyang Zhou
 */
public class SPIRegistryImpl implements SPIRegistry {

	@Override
	public void addExcludedPortletId(String portletId) {
		_excludedPortletIds.add(portletId);
	}

	@Override
	public Set<String> getExcludedPortletIds() {
		return _excludedPortletIds;
	}

	@Override
	public SPI getPortletSPI(String portletId) {
		if (_excludedPortletIds.contains(portletId)) {
			return null;
		}

		return _portletSPIs.get(portletId);
	}

	@Override
	public SPI getServletContextSPI(String servletContextName) {
		return _servletContextSPIs.get(servletContextName);
	}

	@Override
	public void registerSPI(SPI spi) throws RemoteException {
		List<String> portletIds = new ArrayList<String>();

		SPIConfiguration spiConfiguration = spi.getSPIConfiguration();

		for (String portletId : spiConfiguration.getPortletIds()) {
			Portlet portlet = PortletLocalServiceUtil.getPortletById(portletId);

			if (portlet == null) {
				if (_log.isWarnEnabled()) {
					_log.warn("Skip unknown portlet id " + portletId);
				}
			}
			else {
				portletIds.add(portletId);
			}
		}

		String[] servletContextNames =
			spiConfiguration.getServletContextNames();

		for (String servletContextName : servletContextNames) {
			PortletApp portletApp = PortletLocalServiceUtil.getPortletApp(
				servletContextName);

			if (portletApp == null) {
				if (_log.isWarnEnabled()) {
					_log.warn(
						"Skip unknown servlet context name " +
							servletContextName);
				}
			}
			else {
				List<Portlet> portlets = portletApp.getPortlets();

				for (Portlet portlet : portlets) {
					portletIds.add(portlet.getPortletId());
				}
			}
		}

		_lock.lock();

		try {
			for (String portletId : portletIds) {
				_portletSPIs.put(portletId, spi);
			}

			_portletIds.put(
				spi, portletIds.toArray(new String[portletIds.size()]));

			for (String servletContextName : servletContextNames) {
				_servletContextSPIs.put(servletContextName, spi);
			}

			_servletContextNames.put(spi, servletContextNames.clone());

			ServiceBeanAopCacheManagerUtil.reset();
		}
		finally {
			_lock.unlock();
		}
	}

	@Override
	public void removeExcludedPortletId(String portletId) {
		_excludedPortletIds.remove(portletId);
	}

	@Override
	public void unregisterSPI(SPI spi) {
		_lock.lock();

		try {
			String[] portletIds = _portletIds.remove(spi);

			if (portletIds != null) {
				for (String portletId : portletIds) {
					_portletSPIs.remove(portletId);
				}
			}

			String[] servletContextNames = _servletContextNames.remove(spi);

			if (servletContextNames != null) {
				for (String servletContextName : servletContextNames) {
					_servletContextSPIs.remove(servletContextName);
				}
			}
		}
		finally {
			_lock.unlock();
		}
	}

	private static Log _log = LogFactoryUtil.getLog(SPIRegistryImpl.class);

	private Set<String> _excludedPortletIds = new ConcurrentHashSet<String>();
	private Lock _lock = new ReentrantLock();
	private Map<SPI, String[]> _portletIds =
		new ConcurrentHashMap<SPI, String[]>();
	private Map<String, SPI> _portletSPIs =
		new ConcurrentHashMap<String, SPI>();
	private Map<SPI, String[]> _servletContextNames =
		new ConcurrentHashMap<SPI, String[]>();
	private Map<String, SPI> _servletContextSPIs =
		new ConcurrentHashMap<String, SPI>();

}