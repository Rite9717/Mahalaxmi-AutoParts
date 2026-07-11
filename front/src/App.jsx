import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Boxes,
  ClipboardList,
  History,
  IndianRupee,
  PackageCheck,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  Printer,
  ReceiptText,
  RefreshCcw,
  Save,
  Search,
  ShoppingCart,
  Store,
  Trash2,
  Upload,
  Users,
  X
} from 'lucide-react';

function resolveApiBase() {
  if (process.env.REACT_APP_API_BASE) return process.env.REACT_APP_API_BASE;
  if (typeof window === 'undefined') return '/api';
  const { protocol, hostname, port } = window.location;
  if (port === '3000' || port === '5173') {
    return `${protocol}//${hostname}:8080/api`;
  }
  return '/api';
}

const API_BASE = resolveApiBase();
const DEFAULT_ADMIN_PASSWORD = process.env.REACT_APP_ADMIN_PASSWORD || '1234';
const PAGE_SIZE = 50;

function money(value) {
  return `Rs ${Number(value || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatBillDate(value) {
  if (!value) return '-';
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-');
    return `${day}-${month}-${year}`;
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return String(value);
  return parsed.toLocaleDateString('en-GB');
}

async function api(path, options = {}) {
  const isFormData = options.body instanceof FormData;
  const requestOptions = {
    headers: isFormData ? (options.headers || {}) : { 'Content-Type': 'application/json', ...(options.headers || {}) },
    cache: options.cache || 'no-store',
    ...options
  };
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, requestOptions);
  } catch (error) {
    await new Promise((resolve) => setTimeout(resolve, 700));
    response = await fetch(`${API_BASE}${path}`, requestOptions);
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const data = await response.json();
      message = data.message || data.detail || data.error || message;
    } catch {
      // Keep the HTTP fallback.
    }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  const text = await response.text();
  if (!text.trim()) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function apiUrl(path) {
  return `${API_BASE}${path}`;
}

function billLabel(bill) {
  return bill.billNumber || bill.invoiceNumber || `Bill #${bill.id}`;
}

function billAmount(bill) {
  return bill.grandTotal ?? bill.finalAmount ?? 0;
}

function printUrlForBill(bill) {
  if (bill.printUrl) {
    if (bill.printUrl.startsWith('http')) return bill.printUrl;
    return apiUrl(bill.printUrl.replace(/^\/api/, ''));
  }
  return apiUrl(`/bills/${bill.id}/print`);
}

function printUrlForOrder(order) {
  if (order.printUrl) {
    if (order.printUrl.startsWith('http')) return order.printUrl;
    return apiUrl(order.printUrl.replace(/^\/api/, ''));
  }
  return apiUrl(`/orders/${order.id}/print`);
}

function calculatedPurchasePrice(row) {
  if (row.purchaseMode === 'PERCENT') {
    const percent = Math.min(100, Math.max(0, Number(row.purchasePercent || 0)));
    return round2(Number(row.sellingPrice || 0) * (1 - percent / 100));
  }
  return Number(row.purchasePrice || 0);
}

function withPreviewIds(rows) {
  return rows.map((row, index) => ({
    ...row,
    _previewId: row._previewId || `${Date.now()}-${index}-${Math.random().toString(36).slice(2)}`
  }));
}

function App() {
  const [tab, setTab] = useState('billing');
  const [status, setStatus] = useState('');
  const [savingRack, setSavingRack] = useState(false);
  const [stats, setStats] = useState(null);
  const [brands, setBrands] = useState([]);
  const [catalogModels, setCatalogModels] = useState([]);
  const [models, setModels] = useState([]);
  const [parts, setParts] = useState([]);
  const [allParts, setAllParts] = useState([]);
  const [bills, setBills] = useState([]);
  const [ongoingBills, setOngoingBills] = useState([]);
  const [mechanics, setMechanics] = useState([]);
  const [purchases, setPurchases] = useState([]);
  const [manualPurchases, setManualPurchases] = useState([]);
  const [orders, setOrders] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [selectedBrand, setSelectedBrand] = useState('');
  const [selectedModelName, setSelectedModelName] = useState('');
  const [selectedSeries, setSelectedSeries] = useState('');
  const [partSearch, setPartSearch] = useState('');
  const [billingCompany, setBillingCompany] = useState('');
  const [cart, setCart] = useState([]);
  const [adminUnlocked, setAdminUnlocked] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [inventoryFocusSearch, setInventoryFocusSearch] = useState('');
  const [adminPassword] = useState(DEFAULT_ADMIN_PASSWORD);
  const [customer, setCustomer] = useState({
    customerName: 'Walk-in Customer',
    customerGstin: '',
    customerAddress: '',
    customerMobile: '',
    carNumber: '',
    aadhaarNumber: '',
    invoiceType: 'NORMAL',
    billingDate: new Date().toISOString().slice(0, 10),
    supplyType: 'INTRA_STATE',
    paymentMode: 'CASH',
    billType: 'FINAL',
    mechanicId: '',
    jobReference: '',
    paymentStatus: 'PAID',
    amountReceived: '',
    notes: ''
  });

  const loadBills = useCallback(async (query = '') => {
    const billData = await api(`/bills${query}`);
    setBills(billData);
    return billData;
  }, []);

  const clearInventoryFocusSearch = useCallback(() => {
    setInventoryFocusSearch('');
  }, []);

  const refresh = async () => {
    const [brandData, partData, billData, ongoingBillData, mechanicData, purchaseData, manualPurchaseData, orderData, supplierData, statData] = await Promise.all([
      api('/brands'),
      api('/parts'),
      api('/bills'),
      api('/bills/ongoing'),
      api('/mechanics'),
      api('/purchases'),
      api('/manual-purchases'),
      api('/orders'),
      api('/suppliers'),
      api('/dashboard/stats')
    ]);
    setBrands(brandData);
    const modelPairs = await Promise.all(brandData.map(async (brand) => [brand.id, await api(`/brands/${brand.id}/models`).catch(() => [])]));
    const modelMap = new Map(modelPairs);
    setCatalogModels([...modelMap.values()].flat());
    if (selectedBrand) {
      const selectedBrandModels = modelMap.get(Number(selectedBrand)) || modelMap.get(selectedBrand) || [];
      setModels(selectedBrandModels);
    } else {
      setModels([]);
    }
    setAllParts(partData);
    setParts([]);
    setBills(billData);
    setOngoingBills(ongoingBillData);
    setMechanics(mechanicData);
    setPurchases(purchaseData);
    setManualPurchases(manualPurchaseData);
    setOrders(orderData);
    setSuppliers(supplierData);
    setStats(statData);
  };

  useEffect(() => {
    refresh().catch((error) => setStatus(error.message));
    // `refresh` intentionally runs once on mount; button clicks and save flows call it directly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!status) return undefined;
    const timer = window.setTimeout(() => setStatus(''), 5000);
    return () => window.clearTimeout(timer);
  }, [status]);

  useEffect(() => {
    if (!selectedBrand) {
      setModels([]);
      setSelectedModelName('');
      setSelectedSeries('');
      setParts([]);
      return;
    }
    api(`/brands/${selectedBrand}/models`)
      .then((data) => {
        setModels(data);
        setSelectedModelName('');
        setSelectedSeries('');
        setParts([]);
      })
      .catch((error) => setStatus(error.message));
  }, [selectedBrand]);

  const filteredParts = useMemo(() => {
    const needle = partSearch.trim().toLowerCase();
    const source = allParts.filter((part) => {
      if (!selectedBrand) return true;
      const linked = part.compatibleModels || [];
      return linked.some((model) => {
        if (String(model.brandId) !== String(selectedBrand)) return false;
        if (selectedModelName && modelNameKey(model.name) !== modelNameKey(selectedModelName)) return false;
        if (selectedSeries && seriesKey(model.series) !== seriesKey(selectedSeries)) return false;
        return true;
      });
    });
    if (!needle) return source;
    return source.filter((part) =>
      `${part.name} ${part.companyName || ''} ${part.partNumber || ''} ${part.serialNo || ''} ${part.hsnCode || ''} ${part.rackNumber || ''} ${part.sellingPrice || ''}`.toLowerCase().includes(needle)
    );
  }, [allParts, partSearch, selectedBrand, selectedModelName, selectedSeries]);

  const companyOptions = useMemo(() => [...new Set(allParts.map((part) => (part.companyName || '').trim()).filter(Boolean))].sort((a, b) => a.localeCompare(b)), [allParts]);
  const companyFilteredParts = useMemo(() => {
    if (!billingCompany) return filteredParts;
    return filteredParts.filter((part) => (part.companyName || '').trim() === billingCompany);
  }, [filteredParts, billingCompany]);

  const totals = useMemo(() => {
    const subtotal = customer.invoiceType === 'GST'
      ? cart.reduce((sum, item) => sum + item.taxableValue, 0)
      : cart.reduce((sum, item) => sum + item.lineTotal, 0);
    const gstTotal = customer.invoiceType === 'GST' ? cart.reduce((sum, item) => sum + item.gstAmount, 0) : 0;
    const grandTotal = subtotal + gstTotal;
    const cgst = customer.supplyType === 'INTRA_STATE' ? gstTotal / 2 : 0;
    const sgst = customer.supplyType === 'INTRA_STATE' ? gstTotal / 2 : 0;
    const igst = customer.supplyType === 'INTER_STATE' ? gstTotal : 0;
    return { subtotal, gstTotal, cgst, sgst, igst, grandTotal };
  }, [cart, customer.invoiceType, customer.supplyType]);

  const addToCart = (part) => {
    setCart((current) => {
      const existing = current.find((item) => item.partId === part.id);
      if (existing) {
        if (existing.quantity + 1 > part.stockLevel) {
          setStatus(`Only ${part.stockLevel} units available for ${part.name}`);
          return current;
        }
        return current.map((item) => item.partId === part.id ? makeCartLine(part, item.quantity + 1, item.discountAmount) : item);
      }
      if (part.stockLevel < 1) {
        setStatus(`${part.name} is out of stock.`);
        return current;
      }
      return [...current, makeCartLine(part, 1, 0)];
    });
  };

  const updateCart = (partId, patch) => {
    setCart((current) => current.map((item) => {
      if (item.partId !== partId) return item;
      const part = allParts.find((candidate) => candidate.id === partId) || parts.find((candidate) => candidate.id === partId);
      const quantityInput = patch.quantity !== undefined ? cleanNumberInput(patch.quantity) : item.quantity;
      if (quantityInput === '') {
        return makeCartLine(part, '', item.discountAmount);
      }
      const nextQuantity = Math.max(1, Number(quantityInput || 1));
      const safeQuantity = Math.min(part.stockLevel, nextQuantity);
      const gross = Number(part.sellingPrice || 0) * safeQuantity;
      const rawDiscount = Number(cleanSignedNumberInput(patch.discountAmount ?? item.discountAmount) || 0);
      const discount = rawDiscount > 0 ? Math.min(gross, rawDiscount) : rawDiscount;
      return makeCartLine(part, safeQuantity, discount);
    }));
  };

  const saveBill = async () => {
    if (!cart.length) {
      setStatus('Add at least one part before saving a bill.');
      return;
    }
    if (cart.some((item) => Number(item.quantity || 0) < 1)) {
      setStatus('Enter quantity for every item before saving the bill.');
      return;
    }
    if (customer.billType === 'ONGOING' && !customer.mechanicId) {
      setStatus('Select a mechanic before saving an ongoing bill.');
      return;
    }
    const password = window.prompt('Enter admin password to save bill');
    if (password !== adminPassword) {
      setStatus('Wrong password.');
      return;
    }
    const gstin = customer.customerGstin.trim().toUpperCase();
    if (customer.invoiceType === 'GST' && gstin && !/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(gstin)) {
      setStatus('Customer GSTIN must be 15 characters, for example 27ABCDE1234F1Z5. Leave it blank for an unregistered GST bill.');
      return;
    }
    try {
      const bill = await api('/bills', {
        method: 'POST',
        body: JSON.stringify({
          ...customer,
          customerGstin: gstin,
          mechanicId: customer.billType === 'ONGOING' ? Number(customer.mechanicId) : null,
          amountReceived: customer.billType === 'ONGOING'
            ? 0
            : customer.paymentStatus === 'PENDING'
              ? 0
              : customer.paymentStatus === 'PARTIAL'
                ? Number(customer.amountReceived || 0)
                : totals.grandTotal,
          items: cart.map((item) => ({
            partId: item.partId,
            quantity: item.quantity,
            discountAmount: item.discountAmount
          }))
        })
      });
      setStatus(customer.billType === 'ONGOING'
        ? `Saved ongoing bill ${billLabel(bill)}. Stock has been reserved.`
        : `Saved ${billLabel(bill)}. Stock has been deducted.`);
      setCart([]);
      await refresh();
      if (bill.billType !== 'ONGOING') {
        window.open(printUrlForBill(bill), '_blank');
      }
    } catch (error) {
      setStatus(error.message);
    }
  };

  const createPart = async (payload) => {
    try {
      const part = await api('/parts', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setStatus(`${part.name} added to inventory with ${part.stockLevel} units.`);
      await refresh();
      return true;
    } catch (error) {
      setStatus(error.message);
      return false;
    }
  };

  const cancelBill = async (bill) => {
    const ongoing = bill.billType === 'ONGOING';
    const password = window.prompt(`Enter admin password to ${ongoing ? 'delete ongoing bill' : 'cancel'} ${bill.billNumber}`);
    if (password !== adminPassword) {
      setStatus('Wrong password.');
      return;
    }
    if (!window.confirm(`${ongoing ? 'Delete ongoing bill' : 'Cancel bill'} ${bill.billNumber} and restore stock?`)) return;
    try {
      await api(`/bills/${bill.id}/cancel`, { method: 'POST' });
      setStatus(`${bill.billNumber} ${ongoing ? 'deleted from ongoing bills' : 'cancelled'} and stock restored.`);
      await refresh();
    } catch (error) {
      setStatus(error.message);
    }
  };

  const deleteCancelledBill = async (bill) => {
    if (bill.status !== 'CANCELLED') {
      setStatus('Only cancelled bills can be deleted.');
      return;
    }
    const password = window.prompt(`Enter admin password to delete cancelled bill ${bill.billNumber}`);
    if (password !== adminPassword) {
      setStatus('Wrong password.');
      return;
    }
    if (!window.confirm(`Delete cancelled bill ${bill.billNumber}? This cannot be undone.`)) return;
    try {
      await api(`/bills/${bill.id}`, {
        method: 'DELETE',
        headers: { 'X-Admin-Password': password }
      });
      setStatus(`Deleted cancelled bill ${bill.billNumber}.`);
      await refresh();
    } catch (error) {
      setStatus(error.message);
    }
  };

  const createSupplier = async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await api('/suppliers', {
        method: 'POST',
        body: JSON.stringify({
          name: form.get('name'),
          contactPerson: form.get('contactPerson'),
          phone: form.get('phone'),
          address: form.get('address'),
          defaultDiscount: Number(form.get('defaultDiscount') || 0)
        })
      });
      event.currentTarget.reset();
      setStatus('Supplier saved.');
      await refresh();
    } catch (error) {
      setStatus(error.message);
    }
  };

  const createPurchase = async (payload) => {
    try {
      const purchase = await api('/purchases', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setStatus(`Dealer purchase saved. Stock increased by ${purchase.items.reduce((sum, item) => sum + item.quantity, 0)} units.`);
      await refresh();
      return true;
    } catch (error) {
      setStatus(error.message);
      return false;
    }
  };

  const createVehicleModel = async (payload) => {
    const model = await api('/models', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    setStatus(`Vehicle model saved: ${modelLabel(model)}.`);
    await refresh();
    return model;
  };

  const createManualPurchase = async (payload) => {
    try {
      const purchase = await api('/manual-purchases', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setStatus(`Manual dealer purchase saved: ${purchase.dealerName} / ${money(purchase.totalAmount)}.`);
      await refresh();
      return true;
    } catch (error) {
      setStatus(error.message);
      return false;
    }
  };

  const createDealerOrder = async (payload) => {
    try {
      const order = await api('/orders', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setStatus(`Dealer order saved: ${order.orderNumber}.`);
      await refresh();
      return order;
    } catch (error) {
      setStatus(error.message);
      return null;
    }
  };

  const updateDealerOrder = async (orderId, payload) => {
    try {
      const order = await api(`/orders/${orderId}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
      setStatus(`Dealer order updated: ${order.orderNumber}.`);
      await refresh();
      return order;
    } catch (error) {
      setStatus(error.message);
      return null;
    }
  };

  const deleteDealerOrder = async (order) => {
    const password = window.prompt(`Enter admin password to delete ${order.orderNumber}`);
    if (password !== adminPassword) {
      setStatus('Wrong password.');
      return false;
    }
    if (!window.confirm(`Delete dealer order ${order.orderNumber}?`)) return false;
    try {
      await api(`/orders/${order.id}`, {
        method: 'DELETE',
        headers: { 'X-Admin-Password': password }
      });
      setStatus(`Deleted dealer order ${order.orderNumber}.`);
      await refresh();
      return true;
    } catch (error) {
      setStatus(error.message);
      return false;
    }
  };

  const createMechanic = async (payload) => {
    try {
      const mechanic = await api('/mechanics', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setStatus(`Mechanic saved: ${mechanic.mechanicName}.`);
      await refresh();
      return mechanic;
    } catch (error) {
      setStatus(error.message);
      return null;
    }
  };

  const updateMechanic = async (id, payload) => {
    try {
      const mechanic = await api(`/mechanics/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
      setStatus(`Updated mechanic: ${mechanic.mechanicName}.`);
      await refresh();
      return mechanic;
    } catch (error) {
      setStatus(error.message);
      return null;
    }
  };

  const deleteMechanic = async (mechanic) => {
    const password = window.prompt(`Enter admin password to delete ${mechanic.mechanicName}`);
    if (password !== adminPassword) {
      setStatus('Wrong password.');
      return false;
    }
    if (!window.confirm(`Delete ${mechanic.mechanicName}?`)) return false;
    try {
      await api(`/mechanics/${mechanic.id}`, { method: 'DELETE' });
      setStatus(`Deleted ${mechanic.mechanicName}.`);
      await refresh();
      return true;
    } catch (error) {
      setStatus(error.message);
      return false;
    }
  };

  const updateOngoingBillItems = async (bill, items) => {
    try {
      const updated = await api(`/bills/${bill.id}/items`, {
        method: 'PUT',
        body: JSON.stringify({
          invoiceType: bill.invoiceType || 'NORMAL',
          supplyType: bill.supplyType || 'INTRA_STATE',
          items: items.map((item) => ({
            partId: item.partId,
            quantity: item.quantity,
            discountAmount: item.discountAmount || 0
          }))
        })
      });
      setStatus(`Updated ongoing bill ${billLabel(updated)}.`);
      await refresh();
      return updated;
    } catch (error) {
      setStatus(error.message);
      return null;
    }
  };

  const recordPayment = async (bill, payload) => {
    try {
      const updated = await api(`/bills/${bill.id}/payments`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setStatus(`Payment recorded for ${billLabel(updated)}. Balance: ${money(updated.balanceAmount)}.`);
      await refresh();
      return updated;
    } catch (error) {
      setStatus(error.message);
      return null;
    }
  };

  const finalizeOngoingBill = async (bill) => {
    const password = window.prompt(`Enter admin password to finalize ${bill.billNumber}`);
    if (password !== adminPassword) {
      setStatus('Wrong password.');
      return null;
    }
    try {
      const updated = await api(`/bills/${bill.id}/finalize`, { method: 'POST' });
      setStatus(`${updated.billNumber} finalized and moved to Bills History.`);
      await refresh();
      window.open(printUrlForBill(updated), '_blank');
      return updated;
    } catch (error) {
      setStatus(error.message);
      return null;
    }
  };

  const unlockAdmin = (password) => {
    if (password === adminPassword) {
      setAdminUnlocked(true);
      setStatus('Admin access unlocked.');
      return true;
    }
    setStatus('Wrong password.');
    return false;
  };

  const updatePart = async (part, patch) => {
    setSavingRack(true);
    try {
      const payload = {
        imageUrl: part.imageUrl,
        name: patch.name ?? part.name,
        partNumber: patch.partNumber ?? part.partNumber ?? '',
        serialNo: patch.serialNo ?? part.serialNo ?? '',
        hsnCode: patch.hsnCode ?? part.hsnCode ?? '',
        companyName: patch.companyName ?? part.companyName ?? '',
        carCompatibility: patch.carCompatibility ?? part.carCompatibility ?? 'Universal',
        stockLevel: Number(patch.stockLevel ?? part.stockLevel ?? 0),
        warehouseLocation: part.warehouseLocation || 'Main Warehouse',
        section: part.section || '',
        rackNumber: patch.rackNumber ?? part.rackNumber ?? '',
        shelfBin: part.shelfBin || '',
        supplier: part.supplier || '',
        costPrice: Number(patch.costPrice ?? part.costPrice ?? 0),
        sellingPrice: Number(patch.sellingPrice ?? part.sellingPrice ?? 0),
        purchaseDiscount: Number(patch.purchaseDiscount ?? part.purchaseDiscount ?? 0),
        gstRate: Number(part.gstRate || 0),
        modelIds: patch.modelIds ?? (part.compatibleModels || []).map((model) => model.id)
      };
      await api(`/parts/${part.id}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
      setStatus(`Updated ${part.name}.`);
      await refresh();
      return true;
    } catch (error) {
      setStatus(error.message);
      return false;
    } finally {
      setSavingRack(false);
    }
  };

  const fetchPartCompatibility = async (part) => {
    setSavingRack(true);
    try {
      const result = await api(`/parts/${part.id}/fetch-compatibility-openai`, { method: 'POST' });
      setStatus(`${part.name}: ${result.message}`);
      await refresh();
      return result;
    } catch (error) {
      setStatus(error.message);
      return null;
    } finally {
      setSavingRack(false);
    }
  };

  const fetchMissingCompatibility = async () => {
    setSavingRack(true);
    try {
      const results = await api('/parts/fetch-missing-mgp-compatibility-openai?limit=10', { method: 'POST' });
      const added = results.reduce((sum, row) => sum + Number(row.matchedModels || 0), 0);
      const updatedParts = results
        .filter((row) => Number(row.matchedModels || 0) > 0)
        .map((row) => row.part?.name || row.part?.partNumber || 'Item')
        .slice(0, 3);
      const firstUpdatedPart = results.find((row) => Number(row.matchedModels || 0) > 0)?.part;
      if (firstUpdatedPart) {
        setInventoryFocusSearch(firstUpdatedPart.partNumber || firstUpdatedPart.name || '');
      }
      const updatedText = updatedParts.length ? ` Updated: ${updatedParts.join(', ')}${updatedParts.length < results.filter((row) => Number(row.matchedModels || 0) > 0).length ? '...' : ''}` : '';
      setStatus(added > 0
        ? `OpenAI checked ${results.length} MGP item(s). Added ${added} compatibility model(s).${updatedText} Inventory is showing the first updated item.`
        : `OpenAI checked ${results.length} MGP item(s). Added 0 verified model(s).`);
      await refresh();
      return results;
    } catch (error) {
      setStatus(error.message);
      return [];
    } finally {
      setSavingRack(false);
    }
  };

  const deletePart = async (part) => {
    const password = window.prompt(`Enter admin password to delete ${part.name}`);
    if (password !== adminPassword) {
      setStatus('Wrong password.');
      return false;
    }
    if (!window.confirm(`Delete ${part.name} from inventory? This cannot be undone.`)) {
      return false;
    }
    try {
      await api(`/parts/${part.id}`, {
        method: 'DELETE',
        headers: { 'X-Admin-Password': password }
      });
      setStatus(`Deleted ${part.name}.`);
      await refresh();
      return true;
    } catch (error) {
      setStatus(error.message);
      return false;
    }
  };

  const updatePurchase = async (purchaseId, payload) => {
    try {
      const purchase = await api(`/purchases/${purchaseId}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
      setStatus(`Updated dealer purchase ${purchase.dealerInvoiceNumber || purchase.id}.`);
      await refresh();
      return true;
    } catch (error) {
      setStatus(error.message);
      return false;
    }
  };

  const uploadInventoryPdf = async (file) => {
    const form = new FormData();
    form.append('file', file);
    const rows = await api('/inventory/upload-pdf', { method: 'POST', body: form });
    if (!rows.length) {
      setStatus('PDF uploaded, but no inventory rows were detected. This can happen with scanned PDFs or a new dealer layout.');
    } else {
      setStatus(`PDF uploaded. Found ${rows.length} row(s) for review.`);
    }
    return rows;
  };

  const saveInventoryImport = async (rows, allowOverride = false, purchaseInfo = {}) => {
    const saved = await api('/inventory/save-import', {
      method: 'POST',
      body: JSON.stringify({ allowOverride, rows, ...purchaseInfo })
    });
    setStatus(purchaseInfo.recordPurchase
      ? `Imported ${saved.length} inventory item(s) and saved dealer purchase record.`
      : `Imported ${saved.length} inventory item(s) from PDF.`);
    await refresh();
    return saved;
  };

  const modelNameMap = new Map();
  models.forEach((model) => {
    const key = modelNameKey(model.name);
    if (key && !modelNameMap.has(key)) modelNameMap.set(key, model.name);
  });
  const modelNames = [...modelNameMap.values()].sort((a, b) => a.localeCompare(b));
  const seriesMap = new Map();
  models
    .filter((model) => modelNameKey(model.name) === modelNameKey(selectedModelName))
    .forEach((model) => {
      const key = seriesKey(model.series);
      if (key && !seriesMap.has(key)) seriesMap.set(key, model.series);
    });
  const seriesOptions = [...seriesMap.values()].sort((a, b) => seriesKey(a).localeCompare(seriesKey(b)));
  const changeTab = (nextTab) => {
    if (nextTab === 'inventory' || nextTab === 'history' || nextTab === 'ongoing' || nextTab === 'clients') {
      setAdminUnlocked(false);
    }
    setTab(nextTab);
  };

  const tabs = [
    ['billing', ShoppingCart, 'Billing'],
    ['ongoing', ReceiptText, 'Ongoing Bills'],
    ['dashboard', ClipboardList, 'Dashboard'],
    ['inventory', Boxes, 'Inventory'],
    ['orders', ClipboardList, 'Dealer Orders'],
    ['purchases', PackageCheck, 'Dealer Purchases'],
    ['history', History, 'Bills'],
    ['clients', Users, 'Manage Clients'],
    ['suppliers', Users, 'Suppliers']
  ];

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      <div className="flex min-h-screen">
        {sidebarOpen && <aside className="hidden w-72 shrink-0 border-r border-zinc-800 bg-zinc-950 px-4 py-5 lg:block">
          <div className="mb-8 flex items-center gap-3 px-2">
            <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-red-600 text-white">
              <Store size={24} />
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-lg font-black">Mahalaxmi</div>
              <div className="text-xs uppercase tracking-wide text-zinc-500">Auto Parts Web</div>
            </div>
            <button className="btn btn-secondary h-9 w-9 p-0" onClick={() => setSidebarOpen(false)} title="Close side panel">
              <PanelLeftClose size={17} />
            </button>
          </div>
          <nav className="space-y-2">
            {tabs.map(([id, Icon, label]) => (
              <button
                key={id}
                className={`btn w-full justify-start ${tab === id ? 'bg-red-600 text-white' : 'text-zinc-400 hover:bg-zinc-900 hover:text-white'}`}
                onClick={() => changeTab(id)}
              >
                <Icon size={18} /> {label}
              </button>
            ))}
          </nav>
        </aside>}

        <main className="flex-1 overflow-x-hidden">
          <header className="sticky top-0 z-10 border-b border-zinc-800 bg-zinc-950/90 px-4 py-4 backdrop-blur md:px-8">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <h1 className="text-xl font-black md:text-2xl">Counter Operations</h1>
                <p className="text-sm text-zinc-500">Brand/model lookup, rack visibility, GST billing, stock control.</p>
              </div>
              <div className="flex flex-wrap gap-2">
                {!sidebarOpen && (
                  <button className="btn btn-secondary hidden lg:inline-flex" onClick={() => setSidebarOpen(true)} title="Open side panel">
                    <PanelLeftOpen size={16} /> Menu
                  </button>
                )}
                <button className="btn btn-secondary" onClick={() => refresh().catch((error) => setStatus(error.message))}>
                  <RefreshCcw size={16} /> Refresh
                </button>
                <select className="field w-44" value={tab} onChange={(event) => changeTab(event.target.value)}>
                  {tabs.map(([id,, label]) => <option key={id} value={id}>{label}</option>)}
                </select>
              </div>
            </div>
          </header>

          {status && (
            <div className="fixed bottom-5 right-5 z-50 flex max-w-xl items-start gap-3 rounded-md border border-amber-500/30 bg-zinc-950 px-4 py-3 text-sm text-amber-100 shadow-2xl shadow-black/50">
              <span>{status}</span>
              <button className="text-zinc-500 hover:text-white" onClick={() => setStatus('')} aria-label="Close notification">
                <X size={16} />
              </button>
            </div>
          )}

          <div className="p-4 md:p-8">
            {tab === 'billing' && (
              <BillingScreen
                brands={brands}
                models={models}
                modelNames={modelNames}
                seriesOptions={seriesOptions}
                selectedBrand={selectedBrand}
                selectedModelName={selectedModelName}
                selectedSeries={selectedSeries}
                setSelectedBrand={setSelectedBrand}
                setSelectedModelName={setSelectedModelName}
                setSelectedSeries={setSelectedSeries}
                partSearch={partSearch}
                setPartSearch={setPartSearch}
                parts={companyFilteredParts}
                companyOptions={companyOptions}
                selectedCompany={billingCompany}
                setSelectedCompany={setBillingCompany}
                cart={cart}
                customer={customer}
                setCustomer={setCustomer}
                mechanics={mechanics}
                createMechanic={createMechanic}
                totals={totals}
                addToCart={addToCart}
                updateCart={updateCart}
                removeFromCart={(partId) => setCart((current) => current.filter((item) => item.partId !== partId))}
                saveBill={saveBill}
              />
            )}
            {tab === 'ongoing' && (
              adminUnlocked ? (
                <OngoingBills bills={ongoingBills} allParts={allParts} updateOngoingBillItems={updateOngoingBillItems} recordPayment={recordPayment} finalizeOngoingBill={finalizeOngoingBill} cancelBill={cancelBill} />
              ) : (
                <AdminGate title="Ongoing Bills Locked" unlockAdmin={unlockAdmin} />
              )
            )}
            {tab === 'dashboard' && <Dashboard stats={stats} bills={bills} />}
            {tab === 'orders' && <DealerOrders orders={orders} createDealerOrder={createDealerOrder} updateDealerOrder={updateDealerOrder} deleteDealerOrder={deleteDealerOrder} />}
            {tab === 'inventory' && (
              adminUnlocked ? (
                <Inventory parts={allParts} brands={brands} catalogModels={catalogModels} focusSearch={inventoryFocusSearch} clearFocusSearch={clearInventoryFocusSearch} updatePart={updatePart} deletePart={deletePart} fetchPartCompatibility={fetchPartCompatibility} fetchMissingCompatibility={fetchMissingCompatibility} savingRack={savingRack} createPart={createPart} createVehicleModel={createVehicleModel} uploadInventoryPdf={uploadInventoryPdf} saveInventoryImport={saveInventoryImport} />
              ) : (
                <AdminGate title="Inventory Locked" unlockAdmin={unlockAdmin} />
              )
            )}
            {tab === 'purchases' && <Purchases suppliers={suppliers} parts={allParts} purchases={purchases} manualPurchases={manualPurchases} createPurchase={createPurchase} createManualPurchase={createManualPurchase} updatePurchase={updatePurchase} />}
            {tab === 'history' && (
              adminUnlocked ? <BillHistory bills={bills} loadBills={loadBills} cancelBill={cancelBill} deleteCancelledBill={deleteCancelledBill} recordPayment={recordPayment} /> : <AdminGate title="Bills Locked" unlockAdmin={unlockAdmin} />
            )}
            {tab === 'clients' && (
              adminUnlocked ? <ManageClients mechanics={mechanics} createMechanic={createMechanic} updateMechanic={updateMechanic} deleteMechanic={deleteMechanic} ongoingBills={ongoingBills} bills={bills} recordPayment={recordPayment} /> : <AdminGate title="Manage Clients Locked" unlockAdmin={unlockAdmin} />
            )}
            {tab === 'suppliers' && <Suppliers suppliers={suppliers} createSupplier={createSupplier} />}
          </div>
        </main>
      </div>
    </div>
  );
}

function makeCartLine(part, quantity, discountAmount = 0) {
  const numericQuantity = Number(quantity || 0);
  const gross = Number(part.sellingPrice || 0) * numericQuantity;
  const rawDiscount = Number(discountAmount || 0);
  const safeDiscount = rawDiscount > 0 ? Math.min(gross, rawDiscount) : rawDiscount;
  const lineTotal = round2(gross - safeDiscount);
  const gstRate = Number(part.gstRate || 0);
  const taxableValue = gstRate > 0 ? round2(lineTotal * 100 / (100 + gstRate)) : lineTotal;
  const gstAmount = round2(lineTotal - taxableValue);
  return {
    partId: part.id,
    partName: part.name,
    partNumber: part.partNumber || '',
    serialNo: part.serialNo || '',
    hsnCode: part.hsnCode || '',
    companyName: part.companyName || '',
    compatibility: partCompatibility(part),
    rackNumber: part.rackNumber,
    stockLevel: part.stockLevel,
    unitPrice: numericQuantity > 0 ? round2(lineTotal / numericQuantity) : 0,
    gstRate,
    quantity,
    discountAmount: safeDiscount,
    taxableValue,
    gstAmount,
    lineTotal
  };
}

function round2(value) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function cleanNumberInput(value) {
  const cleaned = String(value ?? '').replace(/[^\d.]/g, '').replace(/(\..*)\./g, '$1');
  if (!cleaned) return '';
  if (cleaned.includes('.')) {
    const [whole, decimal] = cleaned.split('.');
    return `${String(Number(whole || 0))}.${decimal}`;
  }
  return String(Number(cleaned));
}

function cleanSignedNumberInput(value) {
  const raw = String(value ?? '').trim();
  const negative = raw.startsWith('-');
  const cleaned = raw.replace(/[^\d.]/g, '').replace(/(\..*)\./g, '$1');
  if (!cleaned) return negative ? '-' : '';
  const normalized = cleaned.includes('.') ? cleaned.replace(/^0+(?=\d)/, '') : String(Number(cleaned));
  return `${negative ? '-' : ''}${normalized}`;
}

function modelLabel(model) {
  if (!model) return 'Unassigned';
  return `${model.brandName} ${model.name} ${model.series || ''}`.trim();
}

function modelNameKey(value) {
  return String(value || '')
    .trim()
    .toUpperCase()
    .replace(/\s+/g, ' ')
    .replace(/^SWIFT DZIRE$/, 'DZIRE')
    .replace(/^WAGON R$/, 'WAGONR')
    .replace(/^WAGONAR$/, 'WAGONR');
}

function seriesKey(value) {
  return String(value || '')
    .trim()
    .toUpperCase()
    .replace(/TYPE-([0-9])/g, 'TYPE $1')
    .replace(/TYPE([0-9])/g, 'TYPE $1')
    .replace(/\s+/g, ' ');
}

function partCompatibility(part) {
  const linked = (part.compatibleModels || []).map(modelLabel).join(', ');
  return linked || part.carCompatibility || 'Universal';
}

function BillingScreen(props) {
  const {
    brands,
    modelNames,
    seriesOptions,
    selectedBrand,
    selectedModelName,
    selectedSeries,
    setSelectedBrand,
    setSelectedModelName,
    setSelectedSeries,
    companyOptions,
    selectedCompany,
    setSelectedCompany,
    partSearch,
    setPartSearch,
    parts,
    cart,
    customer,
    setCustomer,
    mechanics,
    createMechanic,
    totals,
    addToCart,
    updateCart,
    removeFromCart,
    saveBill
  } = props;
  const [page, setPage] = useState(1);
  const [quickMechanic, setQuickMechanic] = useState(false);
  const [mechanicForm, setMechanicForm] = useState({ mechanicName: '', garageName: '' });
  const totalPages = Math.max(1, Math.ceil(parts.length / PAGE_SIZE));
  const pagedParts = parts.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  useEffect(() => {
    setPage(1);
  }, [partSearch, selectedBrand, selectedModelName, selectedSeries, selectedCompany]);

  useEffect(() => {
    if (page > totalPages) setPage(totalPages);
  }, [page, totalPages]);

  return (
    <div className="grid gap-6 2xl:grid-cols-[1.55fr_1fr]">
      <section className="panel p-4 md:p-5">
        <div className="mb-5 grid gap-3 md:grid-cols-4">
          <label className="space-y-1 md:col-span-4">
            <span className="text-xs font-semibold uppercase text-zinc-500">Search inventory</span>
            <div className="relative">
              <Search className="absolute left-3 top-3 text-zinc-500" size={16} />
              <input className="field pl-9" value={partSearch} onChange={(event) => setPartSearch(event.target.value)} placeholder="Type item name, serial no, part number, HSN, or rack" />
            </div>
          </label>
          <label className="space-y-1">
            <span className="text-xs font-semibold uppercase text-zinc-500">Brand</span>
            <select className="field" value={selectedBrand} onChange={(event) => setSelectedBrand(event.target.value)}>
              <option value="">Select brand</option>
              {brands.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
            </select>
          </label>
          <label className="space-y-1">
            <span className="text-xs font-semibold uppercase text-zinc-500">Model</span>
            <select
              className="field"
              value={selectedModelName}
              onChange={(event) => {
                setSelectedModelName(event.target.value);
                setSelectedSeries('');
              }}
              disabled={!selectedBrand}
            >
              <option value="">Select model</option>
              {modelNames.map((name) => <option key={name} value={name}>{name}</option>)}
            </select>
          </label>
          <label className="space-y-1">
            <span className="text-xs font-semibold uppercase text-zinc-500">Series</span>
            <select className="field" value={selectedSeries} onChange={(event) => setSelectedSeries(event.target.value)} disabled={!selectedModelName}>
              <option value="">Select series</option>
              {seriesOptions.map((series) => <option key={seriesKey(series)} value={series}>{series}</option>)}
            </select>
          </label>
          <label className="space-y-1">
            <span className="text-xs font-semibold uppercase text-zinc-500">Company</span>
            <select className="field" value={selectedCompany} onChange={(event) => setSelectedCompany(event.target.value)}>
              <option value="">All companies</option>
              {companyOptions.map((company) => <option key={company} value={company}>{company}</option>)}
            </select>
          </label>
        </div>

        <div className="overflow-hidden rounded-lg border border-zinc-800">
          <table className="w-full min-w-[900px] text-left text-sm">
            <thead className="bg-zinc-900 text-xs uppercase text-zinc-500">
              <tr>
                <th className="px-3 py-3">Part</th>
                <th className="px-3 py-3">Part No.</th>
                <th className="px-3 py-3">Serial No.</th>
                <th className="px-3 py-3">HSN</th>
                <th className="px-3 py-3">Rack</th>
                <th className="px-3 py-3 text-right">Stock</th>
                <th className="px-3 py-3 text-right">Rate</th>
                <th className="px-3 py-3 text-right">GST</th>
                <th className="px-3 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800">
              {pagedParts.map((part) => (
                <tr key={part.id} className="bg-zinc-950/70">
                  <td className="px-3 py-3">
                    <div className="font-semibold text-zinc-100">{part.name}</div>
                    {part.companyName && <div className="text-xs text-zinc-400">{part.companyName}</div>}
                    <div className="text-xs text-zinc-500">{partCompatibility(part)}</div>
                  </td>
                  <td className="px-3 py-3 text-zinc-300">{part.partNumber || '-'}</td>
                  <td className="px-3 py-3 text-zinc-300">{part.serialNo || '-'}</td>
                  <td className="px-3 py-3 text-zinc-300">{part.hsnCode || '-'}</td>
                  <td className="px-3 py-3 text-zinc-300">{part.rackNumber || part.warehouseLocation}</td>
                  <td className="px-3 py-3 text-right">
                    <span className={part.stockLevel < 5 ? 'text-amber-300' : 'text-emerald-300'}>{part.stockLevel}</span>
                  </td>
                  <td className="px-3 py-3 text-right">{money(part.sellingPrice)}</td>
                  <td className="px-3 py-3 text-right">{part.gstRate}%</td>
                  <td className="px-3 py-3 text-right">
                    <button className="btn btn-primary" onClick={() => addToCart(part)} disabled={part.stockLevel < 1}>
                      <ShoppingCart size={15} /> Add
                    </button>
                  </td>
                </tr>
              ))}
              {!parts.length && (
                <tr>
                  <td colSpan="9" className="px-4 py-16 text-center text-zinc-500">
                    Search inventory or select a vehicle filter to add items.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <Pagination page={page} totalItems={parts.length} pageSize={PAGE_SIZE} onPageChange={setPage} />
      </section>

      <section className="panel p-4 md:p-5">
        <div className="mb-4 flex items-center gap-2">
          <ReceiptText className="text-red-400" size={20} />
          <h2 className="text-lg font-black">{customer.invoiceType === 'GST' ? 'GST Bill' : 'Normal Bill'}</h2>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <select className="field md:col-span-2" value={customer.billType} onChange={(event) => setCustomer({ ...customer, billType: event.target.value, paymentMode: event.target.value === 'ONGOING' ? 'CREDIT' : customer.paymentMode, paymentStatus: event.target.value === 'ONGOING' ? 'PENDING' : customer.paymentStatus })}>
            <option value="FINAL">Final Bill</option>
            <option value="ONGOING">Ongoing Bill</option>
          </select>
          <select className="field" value={customer.invoiceType} onChange={(event) => setCustomer({ ...customer, invoiceType: event.target.value, customerGstin: event.target.value === 'NORMAL' ? '' : customer.customerGstin })}>
            <option value="NORMAL">Normal Bill</option>
            <option value="GST">GST Bill</option>
          </select>
          <input className="field" type="date" value={customer.billingDate} onChange={(event) => setCustomer({ ...customer, billingDate: event.target.value })} />
          <input className="field" value={customer.customerName} onChange={(event) => setCustomer({ ...customer, customerName: event.target.value })} placeholder="Customer name" />
          <input className="field" value={customer.customerMobile} onChange={(event) => setCustomer({ ...customer, customerMobile: event.target.value })} placeholder="Customer mobile" />
          <input className="field" value={customer.carNumber} onChange={(event) => setCustomer({ ...customer, carNumber: event.target.value.toUpperCase() })} placeholder="Car number (optional)" />
          <input className="field" value={customer.aadhaarNumber} onChange={(event) => setCustomer({ ...customer, aadhaarNumber: event.target.value })} placeholder="Aadhaar no. (optional)" />
          {customer.invoiceType === 'GST' && <input className="field" value={customer.customerGstin} onChange={(event) => setCustomer({ ...customer, customerGstin: event.target.value.toUpperCase() })} placeholder="Customer GSTIN (optional)" />}
          {customer.invoiceType === 'GST' && (
            <select className="field" value={customer.supplyType} onChange={(event) => setCustomer({ ...customer, supplyType: event.target.value })}>
              <option value="INTRA_STATE">CGST + SGST</option>
              <option value="INTER_STATE">IGST</option>
            </select>
          )}
          <select className="field" value={customer.paymentMode} onChange={(event) => setCustomer({ ...customer, paymentMode: event.target.value })}>
            <option value="CASH">Cash</option>
            <option value="UPI">UPI</option>
            <option value="CARD">Card</option>
            <option value="CREDIT">Credit</option>
          </select>
          {customer.billType === 'FINAL' && (
            <>
              <select className="field" value={customer.paymentStatus} onChange={(event) => setCustomer({ ...customer, paymentStatus: event.target.value, paymentMode: event.target.value === 'PAID' ? customer.paymentMode : 'CREDIT', amountReceived: event.target.value === 'PAID' ? '' : customer.amountReceived })}>
                <option value="PAID">Paid</option>
                <option value="PENDING">Pending credit</option>
                <option value="PARTIAL">Partially paid</option>
              </select>
              {customer.paymentStatus === 'PARTIAL' && (
                <input className="field" inputMode="decimal" value={customer.amountReceived} onChange={(event) => setCustomer({ ...customer, amountReceived: cleanNumberInput(event.target.value) })} placeholder="Amount received" />
              )}
            </>
          )}
          {customer.billType === 'ONGOING' && (
            <>
              <select className="field" value={customer.mechanicId} onChange={(event) => setCustomer({ ...customer, mechanicId: event.target.value })}>
                <option value="">Select mechanic</option>
                {mechanics.map((mechanic) => <option key={mechanic.id} value={mechanic.id}>{mechanic.mechanicName} / {mechanic.garageName}</option>)}
              </select>
              <button className="btn btn-secondary" type="button" onClick={() => setQuickMechanic((current) => !current)}>
                <Users size={16} /> Add New Mechanic
              </button>
              <input className="field md:col-span-2" value={customer.jobReference} onChange={(event) => setCustomer({ ...customer, jobReference: event.target.value })} placeholder="Client name / vehicle number / job reference" />
              {quickMechanic && (
                <div className="grid gap-2 rounded-lg border border-zinc-800 bg-zinc-900/60 p-3 md:col-span-2 md:grid-cols-[1fr_1fr_auto]">
                  <input className="field" value={mechanicForm.mechanicName} onChange={(event) => setMechanicForm({ ...mechanicForm, mechanicName: event.target.value })} placeholder="Mechanic name" />
                  <input className="field" value={mechanicForm.garageName} onChange={(event) => setMechanicForm({ ...mechanicForm, garageName: event.target.value })} placeholder="Garage name" />
                  <button className="btn btn-primary" type="button" onClick={async () => {
                    const saved = await createMechanic(mechanicForm);
                    if (saved) {
                      setCustomer({ ...customer, mechanicId: String(saved.id) });
                      setMechanicForm({ mechanicName: '', garageName: '' });
                      setQuickMechanic(false);
                    }
                  }}>
                    <Save size={16} /> Save
                  </button>
                </div>
              )}
            </>
          )}
          {customer.invoiceType === 'GST' && <textarea className="field h-20 md:col-span-2" value={customer.customerAddress} onChange={(event) => setCustomer({ ...customer, customerAddress: event.target.value })} placeholder="Customer address" />}
          <textarea className="field h-20 md:col-span-2" value={customer.notes} onChange={(event) => setCustomer({ ...customer, notes: event.target.value })} placeholder="Additional notes (optional)" />
        </div>

        <div className="mt-5 space-y-3">
          {cart.map((item) => (
            <div key={item.partId} className="rounded-lg border border-zinc-800 bg-zinc-900/70 p-3">
              <div className="mb-3 flex items-start justify-between gap-3">
                <div>
                  <div className="font-semibold">{item.partName}</div>
                  {item.companyName && <div className="text-xs text-zinc-400">{item.companyName}</div>}
                  <div className="text-xs text-zinc-500">
                    {item.partNumber ? `Part No. ${item.partNumber} / ` : ''}{item.serialNo ? `Serial No. ${item.serialNo} / ` : ''}{item.hsnCode ? `HSN ${item.hsnCode} / ` : ''}Rack {item.rackNumber || 'No rack'} / {item.compatibility}
                  </div>
                </div>
                <button className="text-zinc-500 hover:text-red-300" onClick={() => removeFromCart(item.partId)} aria-label="Remove item">
                  <Trash2 size={17} />
                </button>
              </div>
              <div className="grid grid-cols-[1fr_1fr_auto] items-end gap-2">
                <label className="space-y-1">
                  <span className="text-[10px] font-semibold uppercase text-zinc-500">Qty</span>
                  <input className="field" inputMode="numeric" value={item.quantity} onChange={(event) => updateCart(item.partId, { quantity: event.target.value })} />
                </label>
                <label className="space-y-1">
                  <span className="text-[10px] font-semibold uppercase text-zinc-500">Discount</span>
                  <input className="field" inputMode="decimal" value={item.discountAmount || ''} onChange={(event) => updateCart(item.partId, { discountAmount: event.target.value })} placeholder="Rs off" />
                </label>
                <div className="min-w-28 text-right">
                  <div className="text-[10px] font-semibold uppercase text-zinc-500">Rate</div>
                  <div className="text-sm font-bold">{money(item.lineTotal)}</div>
                </div>
              </div>
            </div>
          ))}
          {!cart.length && <div className="rounded-lg border border-dashed border-zinc-800 p-8 text-center text-zinc-500">Add compatible parts to prepare the bill.</div>}
        </div>

        <div className="mt-5 space-y-2 border-t border-zinc-800 pt-4 text-sm">
          <Total label={customer.invoiceType === 'GST' ? 'Taxable' : 'Subtotal'} value={totals.subtotal} />
          {customer.invoiceType === 'GST' && customer.supplyType === 'INTRA_STATE' && <Total label="CGST 9%" value={totals.cgst} />}
          {customer.invoiceType === 'GST' && customer.supplyType === 'INTRA_STATE' && <Total label="SGST 9%" value={totals.sgst} />}
          {customer.invoiceType === 'GST' && <Total label="IGST" value={totals.igst} />}
          <div className="flex items-center justify-between pt-2 text-lg font-black">
            <span>Grand Total</span>
            <span>{money(totals.grandTotal)}</span>
          </div>
        </div>
        <button className="btn btn-primary mt-5 w-full" onClick={saveBill}>
          <Save size={17} /> {customer.billType === 'ONGOING' ? 'Save Ongoing Bill' : 'Save Final Bill'}
        </button>
      </section>
    </div>
  );
}

function Total({ label, value }) {
  return (
    <div className="flex justify-between text-zinc-300">
      <span>{label}</span>
      <strong>{money(value)}</strong>
    </div>
  );
}

function Pagination({ page, totalItems, pageSize, onPageChange }) {
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const start = totalItems ? ((page - 1) * pageSize) + 1 : 0;
  const end = Math.min(totalItems, page * pageSize);

  return (
    <div className="mt-4 flex flex-col gap-3 border-t border-zinc-800 pt-4 text-sm text-zinc-400 md:flex-row md:items-center md:justify-between">
      <span>{start}-{end} of {totalItems}</span>
      <div className="flex items-center gap-2">
        <button className="btn btn-secondary" type="button" onClick={() => onPageChange(Math.max(1, page - 1))} disabled={page <= 1}>Previous</button>
        <span className="min-w-24 text-center text-zinc-300">Page {page} / {totalPages}</span>
        <button className="btn btn-secondary" type="button" onClick={() => onPageChange(Math.min(totalPages, page + 1))} disabled={page >= totalPages}>Next</button>
      </div>
    </div>
  );
}

function AdminGate({ title, unlockAdmin }) {
  const [password, setPassword] = useState('');

  const submit = (event) => {
    event.preventDefault();
    if (unlockAdmin(password)) {
      setPassword('');
    }
  };

  return (
    <section className="panel mx-auto max-w-md p-5">
      <h2 className="text-lg font-black">{title}</h2>
      <p className="mt-1 text-sm text-zinc-500">Enter admin password to continue.</p>
      <form className="mt-4 space-y-3" onSubmit={submit}>
        <input className="field" type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Password" autoFocus />
        <button className="btn btn-primary w-full" type="submit">Unlock</button>
      </form>
    </section>
  );
}

function Dashboard({ stats, bills = [] }) {
  const today = new Date().toISOString().slice(0, 10);
  const thisMonth = today.slice(0, 7);
  const [reportMode, setReportMode] = useState('DAY');
  const [reportDate, setReportDate] = useState(today);
  const [reportMonth, setReportMonth] = useState(thisMonth);
  const [report, setReport] = useState(null);
  const [reportError, setReportError] = useState('');

  useEffect(() => {
    const query = reportMode === 'MONTH'
      ? `mode=MONTH&month=${reportMonth}`
      : `mode=DAY&date=${reportDate}`;
    api(`/reports/profit?${query}`)
      .then((data) => {
        setReport(data);
        setReportError('');
      })
      .catch((error) => {
        setReport(null);
        setReportError(error.message);
      });
  }, [reportMode, reportDate, reportMonth]);

  const downloadSales = () => {
    const query = reportMode === 'MONTH'
      ? `mode=MONTH&month=${reportMonth}`
      : `mode=DAY&date=${reportDate}`;
    window.open(apiUrl(`/reports/sales.csv?${query}`), '_blank');
  };

  const periodLabel = reportMode === 'MONTH'
    ? new Date(`${reportMonth}-01T00:00:00`).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })
    : new Date(`${reportDate}T00:00:00`).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  const creditCustomers = useMemo(() => {
    const grouped = new Map();
    bills
      .filter((bill) => bill.status !== 'CANCELLED' && Number(bill.balanceAmount || 0) > 0)
      .forEach((bill) => {
        const key = `${(bill.customerName || 'Walk-in Customer').trim()}|${bill.customerMobile || ''}`;
        const row = grouped.get(key) || {
          customerName: bill.customerName || 'Walk-in Customer',
          customerMobile: bill.customerMobile || '',
          balance: 0,
          bills: 0,
          lastBillDate: bill.billingDate
        };
        row.balance += Number(bill.balanceAmount || 0);
        row.bills += 1;
        if (bill.billingDate && (!row.lastBillDate || bill.billingDate > row.lastBillDate)) {
          row.lastBillDate = bill.billingDate;
        }
        grouped.set(key, row);
      });
    return [...grouped.values()].sort((a, b) => b.balance - a.balance);
  }, [bills]);

  if (!stats) return <div className="text-zinc-500">Loading dashboard...</div>;
  return (
    <div className="space-y-6">
      <div className="grid gap-4 md:grid-cols-4">
        <Stat icon={Boxes} label="Active Inventory" value={stats.inventory.active} tone="text-cyan-300" />
        <Stat icon={AlertTriangle} label="Low Stock" value={stats.inventory.lowStock} tone="text-amber-300" />
        <Stat icon={IndianRupee} label="Gross Profit" value={money(stats.grossProfit)} tone={Number(stats.grossProfit || 0) >= 0 ? 'text-emerald-300' : 'text-red-300'} />
        <Stat icon={ReceiptText} label="Today Bills" value={stats.todayBills} tone="text-red-300" />
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        <Stat icon={IndianRupee} label="Sales Revenue" value={money(stats.totalRevenue)} tone="text-emerald-300" />
        <Stat icon={PackageCheck} label="Dealer Purchases" value={money(stats.totalPurchases)} tone="text-cyan-300" />
        <Stat icon={Boxes} label="Inventory Value" value={money(stats.inventoryValue)} tone="text-zinc-200" />
      </div>
      <div className="grid gap-4 md:grid-cols-5">
        <Stat icon={ReceiptText} label="Ongoing Bills" value={stats.ongoingBills || 0} tone="text-amber-300" />
        <Stat icon={IndianRupee} label="Receivable" value={money(stats.totalReceivable)} tone="text-amber-300" />
        <Stat icon={ReceiptText} label="Paid Bills" value={stats.paidBills || 0} tone="text-emerald-300" />
        <Stat icon={AlertTriangle} label="Pending Bills" value={stats.pendingBills || 0} tone="text-red-300" />
        <Stat icon={Users} label="Pending Mechanics" value={stats.mechanicsWithPendingPayments || 0} tone="text-cyan-300" />
      </div>
      <div className="grid gap-6 xl:grid-cols-2">
        <SimpleList title="Recent Bills" items={stats.recentBills.map((bill) => `${bill.billNumber} / ${bill.customer} / ${money(bill.amount)}`)} />
        <SimpleList title="Top Selling Parts" items={stats.topSelling.map((part) => `${part.name} / ${part.partNumber} / ${part.sold} units`)} />
      </div>
      <div className="grid gap-6 xl:grid-cols-2">
        <SimpleList title="Mechanic Outstanding" items={(stats.mechanicOutstanding || []).map((row) => `${row.mechanicName} / ${row.garageName} / ${money(row.totalOutstanding)}`)} />
        <SimpleList title="Recently Paid Bills" items={(stats.recentPayments || []).map((payment) => `${payment.paymentDate} / Bill #${payment.billId} / ${money(payment.amount)}${payment.notes ? ` / ${payment.notes}` : ''}`)} />
      </div>
      <section className="panel p-4">
        <div className="mb-4">
          <h2 className="text-lg font-black">Credit Customer Dashboard</h2>
          <p className="text-sm text-zinc-500">Customers with pending or partially paid final bills.</p>
        </div>
        <div className="overflow-x-auto rounded-lg border border-zinc-800">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="bg-zinc-900 text-xs uppercase text-zinc-500">
              <tr>
                <th className="px-3 py-3">Customer</th>
                <th>Mobile</th>
                <th>Last Bill Date</th>
                <th className="text-right">Pending Bills</th>
                <th className="px-3 text-right">Outstanding</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800">
              {creditCustomers.map((customer) => (
                <tr key={`${customer.customerName}-${customer.customerMobile}`}>
                  <td className="px-3 py-3 font-semibold">{customer.customerName}</td>
                  <td>{customer.customerMobile || '-'}</td>
                  <td>{customer.lastBillDate || '-'}</td>
                  <td className="text-right">{customer.bills}</td>
                  <td className="px-3 text-right font-bold text-amber-300">{money(customer.balance)}</td>
                </tr>
              ))}
              {!creditCustomers.length && (
                <tr>
                  <td colSpan="5" className="px-3 py-10 text-center text-zinc-500">No customer credit pending.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
      <section className="panel p-4">
        <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="text-lg font-black">Sales And Profit Report</h2>
            <p className="text-sm text-zinc-500">Review sales, purchase cost, and profit for the selected business period.</p>
          </div>
          <div className="grid gap-2 md:grid-cols-[160px_190px_auto]">
            <select className="field" value={reportMode} onChange={(event) => setReportMode(event.target.value)}>
              <option value="DAY">Daily</option>
              <option value="MONTH">Monthly</option>
            </select>
            {reportMode === 'DAY' ? (
              <input className="field" type="date" value={reportDate} onChange={(event) => setReportDate(event.target.value)} />
            ) : (
              <input className="field" type="month" value={reportMonth} onChange={(event) => setReportMonth(event.target.value)} />
            )}
            <button className="btn btn-secondary" onClick={downloadSales}>
              <ClipboardList size={16} /> Download Sales
            </button>
          </div>
        </div>
        {reportError && <div className="mb-4 rounded-md border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-100">{reportError}</div>}
        {report && (
          <>
            <div className="mb-4 rounded-lg border border-zinc-800 bg-zinc-900/50 px-4 py-3 text-sm text-zinc-300">
              Showing {reportMode === 'MONTH' ? 'monthly' : 'daily'} sales report for <strong className="text-zinc-100">{periodLabel}</strong>
            </div>
            <div className="mb-4 grid gap-3 md:grid-cols-4">
              <Stat icon={ReceiptText} label="Bills" value={report.billCount} tone="text-cyan-300" />
              <Stat icon={Boxes} label="Parts Sold" value={report.quantitySold} tone="text-zinc-200" />
              <Stat icon={IndianRupee} label="Sales" value={money(report.salesTotal)} tone="text-emerald-300" />
              <Stat icon={IndianRupee} label="Profit / Loss" value={money(report.profitLoss)} tone={Number(report.profitLoss || 0) >= 0 ? 'text-emerald-300' : 'text-red-300'} />
            </div>
            <div className="overflow-x-auto rounded-lg border border-zinc-800">
              <table className="w-full min-w-[1180px] text-left text-sm">
                <thead className="bg-zinc-900 text-xs uppercase text-zinc-500">
                  <tr>
                    <th className="px-3 py-3">Date</th>
                    <th>Bill</th>
                    <th>Customer</th>
                    <th>Items</th>
                    <th>Qty</th>
                    <th>Bill Total</th>
                    <th>Paid / Balance</th>
                    <th>Purchase Cost</th>
                    <th>Profit / Loss</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-800">
                  {report.sales.map((sale, index) => (
                    <tr key={`${sale.billNumber}-${index}`}>
                      <td className="px-3 py-3">{sale.billingDate}</td>
                      <td>{sale.billNumber}</td>
                      <td>
                        <div className="font-semibold">{sale.customerName}</div>
                        <div className="text-xs text-zinc-500">{sale.customerMobile || ''}</div>
                      </td>
                      <td>
                        <div className="font-semibold">{sale.itemCount} item{Number(sale.itemCount || 0) === 1 ? '' : 's'}</div>
                        <div className="text-xs text-zinc-500">{sale.status || ''}</div>
                      </td>
                      <td>{sale.quantity}</td>
                      <td>{money(sale.salesTotal)}</td>
                      <td>
                        <div>{money(sale.amountPaid)}</div>
                        <div className="text-xs text-zinc-500">{money(sale.balanceAmount)}</div>
                      </td>
                      <td>{money(sale.purchaseTotal)}</td>
                      <td className={Number(sale.profitLoss || 0) >= 0 ? 'font-bold text-emerald-300' : 'font-bold text-red-300'}>{money(sale.profitLoss)}</td>
                    </tr>
                  ))}
                  {!report.sales.length && (
                    <tr>
                      <td colSpan="9" className="px-4 py-10 text-center text-zinc-500">No sales found for this selection.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function Stat({ icon: Icon, label, value, tone }) {
  return (
    <div className="stat">
      <Icon className={tone} size={23} />
      <div className="mt-4 text-xs font-semibold uppercase text-zinc-500">{label}</div>
      <div className="mt-1 text-2xl font-black">{value}</div>
    </div>
  );
}

function SimpleList({ title, items }) {
  return (
    <section className="panel p-4">
      <h2 className="mb-4 text-lg font-black">{title}</h2>
      <div className="space-y-2">
        {items.length ? items.map((item) => <div key={item} className="rounded-md bg-zinc-900 px-3 py-2 text-sm text-zinc-300">{item}</div>) : <div className="text-sm text-zinc-500">No data yet.</div>}
      </div>
    </section>
  );
}

function Inventory({ parts, brands, catalogModels, focusSearch, clearFocusSearch, updatePart, deletePart, fetchPartCompatibility, fetchMissingCompatibility, savingRack, createPart, createVehicleModel, uploadInventoryPdf, saveInventoryImport }) {
  const [stockDrafts, setStockDrafts] = useState({});
  const [rackDrafts, setRackDrafts] = useState({});
  const [purchaseDrafts, setPurchaseDrafts] = useState({});
  const [purchaseModes, setPurchaseModes] = useState({});
  const [sellingDrafts, setSellingDrafts] = useState({});
  const [nameDrafts, setNameDrafts] = useState({});
  const [partNumberDrafts, setPartNumberDrafts] = useState({});
  const [serialDrafts, setSerialDrafts] = useState({});
  const [hsnDrafts, setHsnDrafts] = useState({});
  const [companyDrafts, setCompanyDrafts] = useState({});
  const [compatibilityDrafts, setCompatibilityDrafts] = useState({});
  const [compatibilityOpen, setCompatibilityOpen] = useState({});
  const [editingParts, setEditingParts] = useState({});
  const [search, setSearch] = useState('');
  const [companyFilter, setCompanyFilter] = useState('');
  const [showAddForm, setShowAddForm] = useState(false);
  const [importRows, setImportRows] = useState([]);
  const [importing, setImporting] = useState(false);
  const [importMessage, setImportMessage] = useState('');
  const [importPurchase, setImportPurchase] = useState({
    recordPurchase: true,
    supplierName: '',
    dealerInvoiceNumber: '',
    purchaseDate: new Date().toISOString().slice(0, 10),
    notes: ''
  });
  const [page, setPage] = useState(1);
  const companyOptions = [...new Set(parts.map((part) => (part.companyName || '').trim()).filter(Boolean))].sort((a, b) => a.localeCompare(b));
  const visible = parts.filter((part) => {
    const matchesCompany = !companyFilter || (part.companyName || '').trim() === companyFilter;
    const matchesSearch = `${part.name} ${part.companyName || ''} ${part.partNumber || ''} ${part.serialNo || ''} ${part.hsnCode || ''} ${part.rackNumber || ''} ${part.sellingPrice || ''} ${part.costPrice || ''}`.toLowerCase().includes(search.toLowerCase());
    return matchesCompany && matchesSearch;
  });
  const totalPages = Math.max(1, Math.ceil(visible.length / PAGE_SIZE));
  const pagedVisible = visible.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  useEffect(() => {
    setPage(1);
  }, [search, companyFilter]);

  useEffect(() => {
    if (page > totalPages) setPage(totalPages);
  }, [page, totalPages]);

  useEffect(() => {
    if (!focusSearch) return;
    setSearch(focusSearch);
    setCompanyFilter('');
    setPage(1);
    clearFocusSearch?.();
  }, [focusSearch, clearFocusSearch]);

  const updateImportRow = (index, field, value) => {
    setImportRows((rows) => rows.map((row, rowIndex) => rowIndex === index ? { ...row, [field]: value } : row));
  };

  const uploadPdf = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setImporting(true);
    setImportMessage('');
    try {
      const rows = await uploadInventoryPdf(file);
      setImportRows(withPreviewIds(rows));
      if (!rows.length) {
        setImportMessage('No rows were detected in this PDF. Try another dealer PDF or add missing rows manually below.');
      }
    } catch (error) {
      setImportMessage(error.message);
    } finally {
      setImporting(false);
      event.target.value = '';
    }
  };

  const saveRows = async () => {
    const rows = importRows.map((row) => {
      const { _previewId, ...payload } = row;
      return {
        ...payload,
        serialNo: '',
        hsnCode: row.hsnCode || '',
        purchasePrice: calculatedPurchasePrice(row),
        purchaseDiscount: row.purchaseMode === 'PERCENT' ? Number(row.purchasePercent || 0) : 0,
        quantity: Number(row.quantity || 0),
        sellingPrice: Number(row.sellingPrice || 0),
        totalAmount: Number(row.totalAmount || 0)
      };
    });
    await saveInventoryImport(rows, true, importPurchase);
    setImportRows([]);
  };

  const currentCompatibilityIds = (part) => (part.compatibleModels || []).map((model) => String(model.id));

  const draftCompatibilityIds = (part) => compatibilityDrafts[part.id] ?? currentCompatibilityIds(part);

  const setCompatibilitySelection = (part, selectedOptions) => {
    const selected = Array.from(selectedOptions).map((option) => option.value);
    setCompatibilityDrafts({ ...compatibilityDrafts, [part.id]: selected });
  };

  const purchaseModeFor = (part) => purchaseModes[part.id] || 'DIRECT';

  const purchaseInputFor = (part) => purchaseDrafts[part.id] ?? part.costPrice ?? 0;

  const sellingPriceFor = (part) => Number(sellingDrafts[part.id] ?? part.sellingPrice ?? 0);

  const calculatedInventoryPurchasePrice = (part) => {
    const input = Number(purchaseInputFor(part) || 0);
    if (purchaseModeFor(part) === 'PERCENT') {
      const percent = Math.min(100, Math.max(0, input));
      return round2(sellingPriceFor(part) * (1 - percent / 100));
    }
    return round2(input);
  };

  const startEditingPart = (part) => {
    const savedDiscount = Number(part.purchaseDiscount || 0);
    const mode = savedDiscount > 0 ? 'PERCENT' : 'DIRECT';
    setEditingParts({ ...editingParts, [part.id]: true });
    setPurchaseModes({ ...purchaseModes, [part.id]: mode });
    setNameDrafts({ ...nameDrafts, [part.id]: part.name || '' });
    setPartNumberDrafts({ ...partNumberDrafts, [part.id]: part.partNumber || '' });
    setSerialDrafts({ ...serialDrafts, [part.id]: part.serialNo || '' });
    setHsnDrafts({ ...hsnDrafts, [part.id]: part.hsnCode || '' });
    setCompanyDrafts({ ...companyDrafts, [part.id]: part.companyName || '' });
    setRackDrafts({ ...rackDrafts, [part.id]: part.rackNumber || '' });
    setStockDrafts({ ...stockDrafts, [part.id]: part.stockLevel ?? 0 });
    setPurchaseDrafts({ ...purchaseDrafts, [part.id]: mode === 'PERCENT' ? savedDiscount : (part.costPrice ?? 0) });
    setSellingDrafts({ ...sellingDrafts, [part.id]: part.sellingPrice ?? 0 });
  };

  const saveInventoryPart = async (part) => {
    const modelIds = draftCompatibilityIds(part);
    await updatePart(part, {
      name: nameDrafts[part.id] ?? part.name ?? '',
      partNumber: partNumberDrafts[part.id] ?? part.partNumber ?? '',
      serialNo: serialDrafts[part.id] ?? part.serialNo ?? '',
      hsnCode: hsnDrafts[part.id] ?? part.hsnCode ?? '',
      rackNumber: rackDrafts[part.id] ?? part.rackNumber ?? '',
      companyName: companyDrafts[part.id] ?? part.companyName ?? '',
      stockLevel: stockDrafts[part.id] ?? part.stockLevel,
      costPrice: calculatedInventoryPurchasePrice(part),
      sellingPrice: sellingPriceFor(part),
      purchaseDiscount: purchaseModeFor(part) === 'PERCENT' ? Number(purchaseInputFor(part) || 0) : 0,
      modelIds: modelIds.map(Number),
      carCompatibility: modelIds.length ? 'Linked vehicle models' : 'Universal'
    });
    setCompatibilityOpen({ ...compatibilityOpen, [part.id]: false });
    setEditingParts({ ...editingParts, [part.id]: false });
  };

  const saveInventoryPartOnEnter = (event, part) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      saveInventoryPart(part);
    }
  };

  return (
    <section className="space-y-5">
      <div className="panel p-4">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-lg font-black">Import Inventory from PDF</h2>
            <p className="text-sm text-zinc-500">Upload a dealer invoice, review every row, then save confirmed stock.</p>
          </div>
          <label className="btn btn-secondary cursor-pointer">
            <Upload size={16} /> {importing ? 'Reading PDF...' : 'Upload PDF'}
            <input className="hidden" type="file" accept="application/pdf" onChange={uploadPdf} disabled={importing} />
          </label>
        </div>
        {importMessage && <div className="mt-4 rounded-md border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">{importMessage}</div>}

        {(!!importRows.length || importMessage) && (
          <div className="mt-5 overflow-hidden rounded-lg border border-zinc-800">
            <div className="grid gap-3 border-b border-zinc-800 bg-zinc-950/70 p-3 md:grid-cols-[160px_1fr_1fr_170px_1fr]">
              <label className="flex items-center gap-2 text-sm font-semibold text-zinc-200">
                <input
                  type="checkbox"
                  checked={importPurchase.recordPurchase}
                  onChange={(event) => setImportPurchase((current) => ({ ...current, recordPurchase: event.target.checked }))}
                />
                Record Purchase
              </label>
              <input
                className="field"
                value={importPurchase.supplierName}
                onChange={(event) => setImportPurchase((current) => ({ ...current, supplierName: event.target.value }))}
                placeholder="Dealer / supplier name"
              />
              <input
                className="field"
                value={importPurchase.dealerInvoiceNumber}
                onChange={(event) => setImportPurchase((current) => ({ ...current, dealerInvoiceNumber: event.target.value }))}
                placeholder="Dealer invoice number"
              />
              <input
                className="field"
                type="date"
                value={importPurchase.purchaseDate}
                onChange={(event) => setImportPurchase((current) => ({ ...current, purchaseDate: event.target.value }))}
              />
              <input
                className="field"
                value={importPurchase.notes}
                onChange={(event) => setImportPurchase((current) => ({ ...current, notes: event.target.value }))}
                placeholder="Notes"
              />
            </div>
            <table className="w-full min-w-[1320px] text-left text-sm">
              <thead className="bg-zinc-900 text-xs uppercase text-zinc-500">
                <tr>
                  <th>Part No.</th>
                  <th>HSN</th>
                  <th>Rack No. (optional)</th>
                  <th className="px-3 py-3">Item Name</th>
                  <th>Qty</th>
                  <th>Selling Price</th>
                  <th>Purchase Mode</th>
                  <th>Purchase Input</th>
                  <th>Actual Purchase</th>
                  <th>Total</th>
                  <th className="px-3 text-right">Edit</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800">
                {importRows.map((row, index) => (
                  <tr key={row._previewId || index}>
                    <td className="w-40">
                      <input className="field" value={row.partNumber || ''} onChange={(event) => updateImportRow(index, 'partNumber', event.target.value.toUpperCase())} />
                    </td>
                    <td className="w-32">
                      <input className="field" value={row.hsnCode || ''} onChange={(event) => updateImportRow(index, 'hsnCode', event.target.value)} placeholder="Optional" />
                    </td>
                    <td className="w-36">
                      <input className="field" value={row.rackNumber || ''} onChange={(event) => updateImportRow(index, 'rackNumber', event.target.value.toUpperCase())} placeholder="Add later" />
                    </td>
                    <td className="px-3 py-2">
                      <input className="field" value={row.itemName || ''} onChange={(event) => updateImportRow(index, 'itemName', event.target.value)} />
                    </td>
                    <td className="w-24"><input className="field" type="number" min="0" value={row.quantity} onChange={(event) => updateImportRow(index, 'quantity', event.target.value)} /></td>
                    <td className="w-36"><input className="field" type="number" min="0" step="0.01" value={row.sellingPrice} onChange={(event) => updateImportRow(index, 'sellingPrice', event.target.value)} /></td>
                    <td className="w-40">
                      <select className="field" value={row.purchaseMode || 'DIRECT'} onChange={(event) => updateImportRow(index, 'purchaseMode', event.target.value)}>
                        <option value="DIRECT">Direct Price</option>
                        <option value="PERCENT">Percentage</option>
                      </select>
                    </td>
                    <td className="w-36">
                      {row.purchaseMode === 'PERCENT' ? (
                        <input className="field" type="number" min="0" max="100" step="0.01" value={row.purchasePercent || ''} onChange={(event) => updateImportRow(index, 'purchasePercent', event.target.value)} placeholder="10%" />
                      ) : (
                        <input className="field" type="number" min="0" step="0.01" value={row.purchasePrice || ''} onChange={(event) => updateImportRow(index, 'purchasePrice', event.target.value)} placeholder="Price" />
                      )}
                    </td>
                    <td className="w-36 font-semibold text-emerald-300">{money(calculatedPurchasePrice(row))}</td>
                    <td className="w-32 text-zinc-300">{money(row.totalAmount)}</td>
                    <td className="px-3 text-right">
                      <button className="text-zinc-500 hover:text-red-300" onClick={() => setImportRows((rows) => rows.filter((_, rowIndex) => rowIndex !== index))}>
                        <Trash2 size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="flex flex-col gap-3 border-t border-zinc-800 bg-zinc-900/50 p-3 md:flex-row md:justify-between">
              <button className="btn btn-secondary" onClick={() => setImportRows((rows) => [...rows, ...withPreviewIds([{ serialNo: '', itemName: '', partNumber: '', hsnCode: '', rackNumber: '', quantity: 1, sellingPrice: 0, purchaseMode: 'DIRECT', purchasePrice: 0, purchasePercent: '', purchaseDiscount: 0, totalAmount: 0, duplicate: false, sourceLine: '' }])])}>Add Missing Row</button>
              <button className="btn btn-primary" onClick={saveRows}><Save size={16} /> Save Inventory</button>
            </div>
          </div>
        )}
      </div>

      <VehicleModelForm brands={brands} createVehicleModel={createVehicleModel} />

      <div className="panel p-4">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-lg font-black">Inventory</h2>
            <p className="text-sm text-zinc-500">Add new parts, set rack, purchase price, selling price, GST, and vehicle fitment.</p>
          </div>
          <div className="flex flex-col gap-2 md:flex-row md:items-center">
            <button className="btn btn-secondary" disabled={savingRack} onClick={fetchMissingCompatibility} title="Use OpenAI web research to fetch compatibility for items without model links">
              <Search size={16} /> OpenAI Fetch Missing MGP
            </button>
            <button className="btn btn-primary" onClick={() => setShowAddForm((current) => !current)}>
              <PackageCheck size={16} /> {showAddForm ? 'Close Add Item' : 'Add Item Manually'}
            </button>
          </div>
        </div>
        {showAddForm && (
          <ManualPartForm
            brands={brands}
            existingParts={parts}
            onCreatePart={async (payload) => {
              const ok = await createPart(payload);
              if (ok) setShowAddForm(false);
              return ok;
            }}
          />
        )}
      </div>

      <div className="panel p-4">
      <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <h2 className="text-lg font-black">Current Stock</h2>
        <div className="grid gap-2 md:grid-cols-[220px_320px]">
          <select className="field" value={companyFilter} onChange={(event) => setCompanyFilter(event.target.value)}>
            <option value="">All companies</option>
            {companyOptions.map((company) => <option key={company} value={company}>{company}</option>)}
          </select>
          <input className="field" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search inventory" />
        </div>
      </div>
      <div className="overflow-x-auto rounded-lg border border-zinc-800">
        <table className="w-full min-w-[1820px] border-separate border-spacing-0 text-left text-sm">
          <thead className="bg-zinc-900 text-xs uppercase text-zinc-500">
            <tr>
              <th className="px-4 py-3">Part</th>
              <th className="px-4 py-3">Company</th>
              <th className="px-4 py-3">Serial No.</th>
              <th className="px-4 py-3">HSN</th>
              <th className="px-4 py-3">Compatibility</th>
              <th className="px-4 py-3">Rack No.</th>
              <th className="px-4 py-3">Purchase</th>
              <th className="px-4 py-3 text-right">Selling</th>
              <th className="px-4 py-3 text-right">Stock</th>
              <th className="px-4 py-3 text-right">Save</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-800">
            {pagedVisible.map((part) => {
              const isEditing = !!editingParts[part.id];
              return (
              <tr key={part.id}>
                <td className="w-80 px-4 py-3 align-top">
                  {isEditing ? (
                    <div className="space-y-2">
                      <input
                        className="field"
                        value={nameDrafts[part.id] ?? part.name ?? ''}
                        onChange={(event) => setNameDrafts({ ...nameDrafts, [part.id]: event.target.value })}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault();
                            saveInventoryPart(part);
                          }
                        }}
                        placeholder="Part name"
                      />
                      <input
                        className="field"
                        value={partNumberDrafts[part.id] ?? part.partNumber ?? ''}
                        onChange={(event) => setPartNumberDrafts({ ...partNumberDrafts, [part.id]: event.target.value.toUpperCase() })}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault();
                            saveInventoryPart(part);
                          }
                        }}
                        placeholder="Part number"
                      />
                    </div>
                  ) : (
                    <div>
                      <strong>{part.name}</strong>
                      <div className="text-xs text-zinc-500">{part.partNumber || 'No part number'}</div>
                    </div>
                  )}
                </td>
                <td className="w-48 px-4 py-3 align-top">
                  {isEditing ? (
                    <input
                      className="field"
                      value={companyDrafts[part.id] ?? part.companyName ?? ''}
                      onChange={(event) => setCompanyDrafts({ ...companyDrafts, [part.id]: event.target.value })}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          saveInventoryPart(part);
                        }
                      }}
                      placeholder="Company / brand"
                    />
                  ) : (
                    <span className="text-zinc-300">{part.companyName || '-'}</span>
                  )}
                </td>
                <td className="w-40 px-4 py-3 align-top">
                  {isEditing ? (
                    <input
                      className="field"
                      value={serialDrafts[part.id] ?? part.serialNo ?? ''}
                      onChange={(event) => setSerialDrafts({ ...serialDrafts, [part.id]: event.target.value })}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          saveInventoryPart(part);
                        }
                      }}
                      placeholder="Serial no."
                    />
                  ) : (
                    <span className="text-zinc-300">{part.serialNo || '-'}</span>
                  )}
                </td>
                <td className="w-36 px-4 py-3 align-top">
                  {isEditing ? (
                    <input
                      className="field"
                      value={hsnDrafts[part.id] ?? part.hsnCode ?? ''}
                      onChange={(event) => setHsnDrafts({ ...hsnDrafts, [part.id]: event.target.value })}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          saveInventoryPart(part);
                        }
                      }}
                      placeholder="HSN"
                    />
                  ) : (
                    <span className="text-zinc-300">{part.hsnCode || '-'}</span>
                  )}
                </td>
                <td className="w-[470px] px-4 py-3 align-top">
                  <div className="mb-2 flex items-start gap-2">
                    <div className="flex flex-1 flex-wrap gap-1">
                      {(part.compatibleModels || []).length ? part.compatibleModels.map((model) => (
                        <span key={model.id} className="rounded bg-zinc-800 px-2 py-1 text-xs text-zinc-200">{modelLabel(model)}</span>
                      )) : <span className="text-xs text-zinc-500">{part.carCompatibility || 'Universal'}</span>}
                    </div>
                    <button
                      className="flex h-7 w-7 shrink-0 items-center justify-center rounded border border-zinc-700 bg-zinc-900 text-sm font-bold text-zinc-100 hover:border-red-500 hover:text-red-300"
                      type="button"
                      onClick={() => setCompatibilityOpen({ ...compatibilityOpen, [part.id]: !compatibilityOpen[part.id] })}
                      title="Edit compatibility"
                    >
                      +
                    </button>
                    <button
                      className="flex h-7 w-7 shrink-0 items-center justify-center rounded border border-zinc-700 bg-zinc-900 text-zinc-100 hover:border-emerald-500 hover:text-emerald-300 disabled:cursor-not-allowed disabled:opacity-50"
                      type="button"
                      disabled={savingRack || !part.partNumber}
                      onClick={() => fetchPartCompatibility(part)}
                      title={part.partNumber ? 'Fetch compatibility with OpenAI web research' : 'Part number needed for online lookup'}
                    >
                      <Search size={14} />
                    </button>
                  </div>
                  {compatibilityOpen[part.id] && (
                    <select
                      className="field min-h-32"
                      multiple
                      value={draftCompatibilityIds(part)}
                      onChange={(event) => setCompatibilitySelection(part, event.target.selectedOptions)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          saveInventoryPart(part);
                        }
                      }}
                    >
                      {catalogModels.map((model) => <option key={model.id} value={String(model.id)}>{modelLabel(model)}</option>)}
                    </select>
                  )}
                </td>
                <td className="w-36 px-4 py-3 align-top">
                  {isEditing ? (
                    <input className="field" value={rackDrafts[part.id] ?? part.rackNumber ?? ''} onChange={(event) => setRackDrafts({ ...rackDrafts, [part.id]: event.target.value.toUpperCase() })} onKeyDown={(event) => saveInventoryPartOnEnter(event, part)} placeholder="Rack" />
                  ) : (
                    <span className="text-zinc-300">{part.rackNumber || '-'}</span>
                  )}
                </td>
                <td className="w-80 px-4 py-3 align-top">
                  {isEditing ? (
                    <div className="grid grid-cols-[130px_1fr] gap-2">
                      <select
                        className="field"
                        value={purchaseModeFor(part)}
                        onKeyDown={(event) => saveInventoryPartOnEnter(event, part)}
                        onChange={(event) => {
                          setPurchaseModes({ ...purchaseModes, [part.id]: event.target.value });
                          setPurchaseDrafts({ ...purchaseDrafts, [part.id]: event.target.value === 'PERCENT' ? (part.purchaseDiscount || '') : (part.costPrice || 0) });
                        }}
                      >
                        <option value="DIRECT">Price</option>
                        <option value="PERCENT">Discount %</option>
                      </select>
                      <input
                        className="field"
                        inputMode="decimal"
                        value={purchaseInputFor(part)}
                        onChange={(event) => setPurchaseDrafts({ ...purchaseDrafts, [part.id]: cleanNumberInput(event.target.value) })}
                        onKeyDown={(event) => saveInventoryPartOnEnter(event, part)}
                        placeholder={purchaseModeFor(part) === 'PERCENT' ? '10%' : 'Purchase price'}
                      />
                    </div>
                  ) : (
                    <span className="text-zinc-300">{money(part.costPrice)}</span>
                  )}
                </td>
                <td className="w-36 px-4 py-3 align-top">
                  {isEditing ? (
                    <input
                      className="field text-right"
                      inputMode="decimal"
                      value={sellingDrafts[part.id] ?? part.sellingPrice ?? 0}
                      onChange={(event) => setSellingDrafts({ ...sellingDrafts, [part.id]: cleanNumberInput(event.target.value) })}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          saveInventoryPart(part);
                        }
                      }}
                      placeholder="Selling price"
                    />
                  ) : (
                    <span className="block text-right text-zinc-300 whitespace-nowrap">{money(part.sellingPrice)}</span>
                  )}
                </td>
                <td className="w-32 px-4 py-3 align-top">
                  {isEditing ? (
                    <input className="field" type="number" min="0" value={stockDrafts[part.id] ?? part.stockLevel} onChange={(event) => setStockDrafts({ ...stockDrafts, [part.id]: event.target.value })} onKeyDown={(event) => saveInventoryPartOnEnter(event, part)} />
                  ) : (
                    <span className="block text-right text-zinc-300">{part.stockLevel}</span>
                  )}
                </td>
                <td className="w-28 px-4 py-3 text-right align-top">
                  <div className="flex justify-end gap-2">
                    {isEditing ? (
                      <button className="btn btn-secondary" disabled={savingRack} onClick={async () => {
                        await saveInventoryPart(part);
                      }} title="Save item"><PackageCheck size={16} /></button>
                    ) : (
                      <button className="btn btn-secondary" disabled={savingRack} onClick={() => startEditingPart(part)} title="Edit item"><Pencil size={16} /></button>
                    )}
                    <button className="btn btn-danger" disabled={savingRack} onClick={() => deletePart(part)} title="Delete item"><Trash2 size={16} /></button>
                  </div>
                </td>
              </tr>
            );})}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalItems={visible.length} pageSize={PAGE_SIZE} onPageChange={setPage} />
      </div>
    </section>
  );
}

function VehicleModelForm({ brands, createVehicleModel }) {
  const [brandMode, setBrandMode] = useState('EXISTING');
  const [brandId, setBrandId] = useState('');
  const [brandName, setBrandName] = useState('');
  const [modelName, setModelName] = useState('');
  const [series, setSeries] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    const selectedBrand = brands.find((brand) => String(brand.id) === String(brandId));
    const finalBrandName = brandMode === 'NEW' ? brandName : selectedBrand?.name;
    if (!finalBrandName || !modelName) return;
    setSaving(true);
    try {
      await createVehicleModel({
        brandName: finalBrandName,
        modelName,
        series
      });
      setModelName('');
      setSeries('');
      if (brandMode === 'NEW') setBrandName('');
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="panel p-4" onSubmit={submit}>
      <div className="mb-4">
        <h2 className="text-lg font-black">Add Missing Car Model</h2>
        <p className="text-sm text-zinc-500">Use this when a brand, model, or type is missing from compatibility.</p>
      </div>
      <div className="grid gap-3 md:grid-cols-5">
        <select className="field" value={brandMode} onChange={(event) => setBrandMode(event.target.value)}>
          <option value="EXISTING">Existing Brand</option>
          <option value="NEW">New Brand</option>
        </select>
        {brandMode === 'EXISTING' ? (
          <select className="field" value={brandId} onChange={(event) => setBrandId(event.target.value)} required>
            <option value="">Brand</option>
            {brands.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
          </select>
        ) : (
          <input className="field" value={brandName} onChange={(event) => setBrandName(event.target.value)} placeholder="Brand name" required />
        )}
        <input className="field" value={modelName} onChange={(event) => setModelName(event.target.value)} placeholder="Model name" required />
        <input className="field" value={series} onChange={(event) => setSeries(event.target.value)} placeholder="Type / series" />
        <button className="btn btn-secondary" disabled={saving}>{saving ? 'Saving...' : 'Add Model'}</button>
      </div>
    </form>
  );
}

function ManualPartForm({ brands, existingParts, onCreatePart }) {
  const [brandId, setBrandId] = useState('');
  const [models, setModels] = useState([]);
  const [modelName, setModelName] = useState('');
  const [seriesId, setSeriesId] = useState('');
  const [priceMode, setPriceMode] = useState('DIRECT');
  const [formError, setFormError] = useState('');
  const [form, setForm] = useState({
    name: '',
    partNumber: '',
    serialNo: '',
    hsnCode: '',
    companyName: '',
    stockLevel: '0',
    warehouseLocation: 'Main Warehouse',
    section: '',
    rackNumber: '',
    shelfBin: '',
    supplier: '',
    costPrice: '',
    sellingPrice: '',
    purchaseDiscount: '0',
    gstRate: '18'
  });

  useEffect(() => {
    if (!brandId) {
      setModels([]);
      setModelName('');
      setSeriesId('');
      return;
    }
    api(`/brands/${brandId}/models`)
      .then((data) => {
        setModels(data);
        setModelName('');
        setSeriesId('');
      })
      .catch(() => {
        setModels([]);
      });
  }, [brandId]);

  const modelNames = [...new Set(models.map((model) => model.name))].sort();
  const seriesOptions = models.filter((model) => model.name === modelName);
  const selectedSeries = models.find((model) => String(model.id) === String(seriesId));
  const selectedBrand = brands.find((brand) => String(brand.id) === String(brandId));

  const update = (field, value) => setForm((current) => ({ ...current, [field]: value }));

  const purchasePrice = priceMode === 'DISCOUNT'
    ? round2(Number(form.sellingPrice || 0) * (1 - Number(form.purchaseDiscount || 0) / 100))
    : Number(form.costPrice || 0);

  const duplicateExists = () => {
    const partNumber = form.partNumber.trim().toUpperCase();
    const name = form.name.trim().toLowerCase();
    if (partNumber) {
      return existingParts.some((part) => part.active !== false && String(part.partNumber || '').trim().toUpperCase() === partNumber);
    }
    if (name) {
      return existingParts.some((part) => part.active !== false && String(part.name || '').trim().toLowerCase() === name);
    }
    return false;
  };

  const submit = async (event) => {
    event.preventDefault();
    setFormError('');
    if (duplicateExists()) {
      setFormError('Item already present in inventory.');
      return;
    }
    const compatibility = selectedSeries
      ? `${selectedBrand?.name || ''} ${selectedSeries.name} ${selectedSeries.series || ''}`.trim()
      : 'Universal';
    await onCreatePart({
      imageUrl: null,
      name: form.name,
      partNumber: form.partNumber,
      serialNo: form.serialNo,
      hsnCode: form.hsnCode,
      companyName: form.companyName,
      carCompatibility: compatibility,
      stockLevel: Number(form.stockLevel || 0),
      warehouseLocation: form.warehouseLocation,
      section: form.section,
      rackNumber: form.rackNumber,
      shelfBin: form.shelfBin,
      supplier: form.supplier,
      costPrice: purchasePrice,
      sellingPrice: Number(form.sellingPrice || 0),
      purchaseDiscount: priceMode === 'DISCOUNT' ? Number(form.purchaseDiscount || 0) : 0,
      gstRate: Number(form.gstRate || 18),
      modelIds: seriesId ? [Number(seriesId)] : []
    });
  };

  return (
    <form className="mt-5 rounded-lg border border-zinc-800 bg-zinc-900/50 p-4" onSubmit={submit}>
      {formError && <div className="mb-3 rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-100">{formError}</div>}
      <div className="mb-4 grid gap-3 md:grid-cols-6">
        <input className="field" value={form.name} onChange={(event) => update('name', event.target.value)} placeholder="Part name" required />
        <input className="field" value={form.partNumber} onChange={(event) => update('partNumber', event.target.value.toUpperCase())} placeholder="Part number / SKU (optional)" />
        <input className="field" value={form.serialNo} onChange={(event) => update('serialNo', event.target.value)} placeholder="Serial no. (optional)" />
        <input className="field" value={form.hsnCode} onChange={(event) => update('hsnCode', event.target.value)} placeholder="HSN (optional)" />
        <input className="field" value={form.companyName} onChange={(event) => update('companyName', event.target.value)} placeholder="Company name" />
        <input className="field" type="number" min="0" value={form.stockLevel} onChange={(event) => update('stockLevel', event.target.value)} placeholder="Opening stock" required />
      </div>

      <div className="mb-4 grid gap-3 md:grid-cols-4">
        <select className="field" value={brandId} onChange={(event) => setBrandId(event.target.value)}>
          <option value="">Brand</option>
          {brands.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
        </select>
        <select className="field" value={modelName} onChange={(event) => {
          setModelName(event.target.value);
          setSeriesId('');
        }} disabled={!brandId}>
          <option value="">Model</option>
          {modelNames.map((name) => <option key={name} value={name}>{name}</option>)}
        </select>
        <select className="field" value={seriesId} onChange={(event) => setSeriesId(event.target.value)} disabled={!modelName}>
          <option value="">Series</option>
          {seriesOptions.map((model) => <option key={model.id} value={model.id}>{model.series || 'No series'}</option>)}
        </select>
        <input className="field" value={form.rackNumber} onChange={(event) => update('rackNumber', event.target.value.toUpperCase())} placeholder="Rack number" />
      </div>

      <div className="grid gap-3 md:grid-cols-5">
        <select className="field" value={priceMode} onChange={(event) => setPriceMode(event.target.value)}>
          <option value="DIRECT">Direct Purchase</option>
          <option value="DISCOUNT">Dealer Discount %</option>
        </select>
        {priceMode === 'DIRECT' ? (
          <input className="field" type="number" min="0" step="0.01" value={form.costPrice} onChange={(event) => update('costPrice', event.target.value)} placeholder="Purchase price" />
        ) : (
          <input className="field" type="number" min="0" max="100" step="0.01" value={form.purchaseDiscount} onChange={(event) => update('purchaseDiscount', event.target.value)} placeholder="Dealer discount %" />
        )}
        <input className="field" type="number" min="0" step="0.01" value={form.sellingPrice} onChange={(event) => update('sellingPrice', event.target.value)} placeholder="Selling price" required />
        <input className="field" type="number" min="0" max="100" step="0.01" value={form.gstRate} onChange={(event) => update('gstRate', event.target.value)} placeholder="GST %" />
        <button className="btn btn-primary" type="submit">
          <Save size={16} /> Save Item
        </button>
      </div>
    </form>
  );
}

function Purchases({ suppliers, parts, purchases, manualPurchases, createPurchase, createManualPurchase, updatePurchase }) {
  const [supplierId, setSupplierId] = useState('');
  const [dealerInvoiceNumber, setDealerInvoiceNumber] = useState('');
  const [manualEntry, setManualEntry] = useState({ dealerName: '', quantity: 1, price: '', purchaseDate: new Date().toISOString().slice(0, 10) });
  const [selectedPartId, setSelectedPartId] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [unitCost, setUnitCost] = useState('');
  const [purchaseMode, setPurchaseMode] = useState('DIRECT');
  const [discountPercentage, setDiscountPercentage] = useState(0);
  const [items, setItems] = useState([]);
  const [editingPurchaseId, setEditingPurchaseId] = useState(null);
  const [purchaseDrafts, setPurchaseDrafts] = useState({});

  const selectedPart = parts.find((part) => String(part.id) === String(selectedPartId));
  const total = items.reduce((sum, item) => sum + item.lineTotal, 0);

  const addPurchaseItem = () => {
    if (!selectedPart || Number(quantity) < 1 || Number(unitCost) < 0) return;
    const finalUnitCost = purchaseMode === 'DISCOUNT'
      ? round2(Number(selectedPart.sellingPrice || 0) * (1 - Number(discountPercentage || 0) / 100))
      : Number(unitCost || 0);
    const lineTotal = round2(Number(quantity) * Number(selectedPart.sellingPrice || 0));
    setItems((current) => [
      ...current,
      {
        partId: selectedPart.id,
        partName: selectedPart.name,
        partNumber: selectedPart.partNumber,
        currentStock: selectedPart.stockLevel,
        quantity: Number(quantity),
        unitCost: finalUnitCost,
        sellingPrice: Number(selectedPart.sellingPrice || 0),
        discountPercentage: purchaseMode === 'DISCOUNT' ? Number(discountPercentage || 0) : 0,
        lineTotal
      }
    ]);
    setSelectedPartId('');
    setQuantity(1);
    setUnitCost('');
    setDiscountPercentage(0);
  };

  const savePurchase = async () => {
    if (!supplierId || !items.length) return;
    const ok = await createPurchase({
      supplierId: Number(supplierId),
      dealerInvoiceNumber,
      purchaseDate: new Date().toISOString().slice(0, 10),
      notes: 'Dealer purchase entered from web app',
      items: items.map((item) => ({
        partId: item.partId,
        quantity: item.quantity,
        unitCost: item.unitCost,
        discountPercentage: item.discountPercentage
      }))
    });
    if (ok) {
      setDealerInvoiceNumber('');
      setItems([]);
    }
  };

  const saveManualEntry = async () => {
    const ok = await createManualPurchase({
      dealerName: manualEntry.dealerName,
      quantity: Number(manualEntry.quantity || 0),
      price: Number(manualEntry.price || 0),
      purchaseDate: manualEntry.purchaseDate
    });
    if (ok) {
      setManualEntry({ dealerName: '', quantity: 1, price: '', purchaseDate: new Date().toISOString().slice(0, 10) });
    }
  };

  const startEditPurchase = (purchase) => {
    setEditingPurchaseId(purchase.id);
    setPurchaseDrafts({
      supplierName: purchase.supplierName || '',
      dealerInvoiceNumber: purchase.dealerInvoiceNumber || '',
      grandTotal: purchase.grandTotal ?? 0
    });
  };

  const saveEditedPurchase = async (purchase) => {
    const ok = await updatePurchase(purchase.id, {
      supplierName: purchaseDrafts.supplierName || '',
      dealerInvoiceNumber: purchaseDrafts.dealerInvoiceNumber || '',
      grandTotal: Number(purchaseDrafts.grandTotal || 0)
    });
    if (ok) {
      setEditingPurchaseId(null);
      setPurchaseDrafts({});
    }
  };

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_1fr]">
      <section className="panel p-4">
        <div className="mb-6 rounded-lg border border-zinc-800 bg-zinc-950/70 p-3">
          <h2 className="mb-3 text-base font-black">Manual Dealer Purchase</h2>
          <div className="grid gap-3 md:grid-cols-[1fr_120px_150px_160px_auto]">
            <input className="field" value={manualEntry.dealerName} onChange={(event) => setManualEntry({ ...manualEntry, dealerName: event.target.value })} placeholder="Dealer name" />
            <input className="field" inputMode="numeric" value={manualEntry.quantity} onChange={(event) => setManualEntry({ ...manualEntry, quantity: cleanNumberInput(event.target.value) })} placeholder="Qty" />
            <input className="field" inputMode="decimal" value={manualEntry.price} onChange={(event) => setManualEntry({ ...manualEntry, price: cleanNumberInput(event.target.value) })} placeholder="Price" />
            <input className="field" type="date" value={manualEntry.purchaseDate} onChange={(event) => setManualEntry({ ...manualEntry, purchaseDate: event.target.value })} />
            <button className="btn btn-primary" type="button" onClick={saveManualEntry} disabled={!manualEntry.dealerName.trim() || Number(manualEntry.quantity || 0) < 1}>
              <Save size={16} /> Save
            </button>
          </div>
          <div className="mt-2 text-sm text-zinc-500">Total: {money(Number(manualEntry.quantity || 0) * Number(manualEntry.price || 0))}</div>
        </div>

        <div className="mb-4">
          <h2 className="text-lg font-black">Dealer Purchase Entry</h2>
          <p className="text-sm text-zinc-500">Use this when you buy parts from dealers. It increases inventory and updates the purchase price used internally.</p>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <select className="field" value={supplierId} onChange={(event) => setSupplierId(event.target.value)}>
            <option value="">Select dealer</option>
            {suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
          </select>
          <input className="field" value={dealerInvoiceNumber} onChange={(event) => setDealerInvoiceNumber(event.target.value)} placeholder="Dealer invoice no." />
          <select className="field md:col-span-2" value={selectedPartId} onChange={(event) => {
            setSelectedPartId(event.target.value);
            const part = parts.find((candidate) => String(candidate.id) === event.target.value);
            setUnitCost(part ? String(part.costPrice || 0) : '');
          }}>
            <option value="">Select purchased part</option>
            {parts.map((part) => <option key={part.id} value={part.id}>{part.name} / {part.partNumber} / stock {part.stockLevel}</option>)}
          </select>
          <input className="field" type="number" min="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} placeholder="Quantity" />
          <select className="field" value={purchaseMode} onChange={(event) => setPurchaseMode(event.target.value)}>
            <option value="DIRECT">Direct Price</option>
            <option value="DISCOUNT">Discount %</option>
          </select>
          {purchaseMode === 'DIRECT' ? (
            <input className="field" type="number" min="0" step="0.01" value={unitCost} onChange={(event) => setUnitCost(event.target.value)} placeholder="Purchase price per unit" />
          ) : (
            <input className="field" type="number" min="0" max="100" step="0.01" value={discountPercentage} onChange={(event) => setDiscountPercentage(event.target.value)} placeholder="Dealer discount %" />
          )}
          <button className="btn btn-secondary" type="button" onClick={addPurchaseItem}>Add Purchase Line</button>
        </div>

        <div className="mt-5 space-y-2">
          {items.map((item, index) => (
            <div key={`${item.partId}-${index}`} className="flex items-center justify-between gap-3 rounded-lg border border-zinc-800 bg-zinc-900 p-3 text-sm">
              <div>
                <div className="font-semibold">{item.partName}</div>
                <div className="text-xs text-zinc-500">{item.partNumber} / Qty {item.quantity} / Purchase {money(item.unitCost)} / Selling {money(item.sellingPrice)}</div>
              </div>
              <div className="flex items-center gap-3">
                <strong>{money(item.lineTotal)}</strong>
                <button className="text-zinc-500 hover:text-red-300" onClick={() => setItems((current) => current.filter((_, i) => i !== index))}>
                  <Trash2 size={16} />
                </button>
              </div>
            </div>
          ))}
          {!items.length && <div className="rounded-lg border border-dashed border-zinc-800 p-8 text-center text-zinc-500">Add parts bought from a dealer.</div>}
        </div>
        <div className="mt-5 flex items-center justify-between border-t border-zinc-800 pt-4">
          <strong>Amount</strong>
          <strong>{money(total)}</strong>
        </div>
        <button className="btn btn-primary mt-4 w-full" onClick={savePurchase} disabled={!supplierId || !items.length}>
          <Save size={17} /> Save Purchase + Add Stock
        </button>
      </section>

      <section className="panel p-4">
        <h2 className="mb-4 text-lg font-black">Recent Dealer Purchases</h2>
        <div className="mb-5 space-y-2">
          <div className="text-xs font-semibold uppercase text-zinc-500">Manual entries</div>
          {manualPurchases.map((purchase) => (
            <div key={purchase.id} className="flex items-center justify-between gap-3 rounded-lg border border-zinc-800 bg-zinc-950/70 p-3 text-sm">
              <div>
                <div className="font-semibold">{purchase.dealerName}</div>
                <div className="text-xs text-zinc-500">{purchase.purchaseDate} / Qty {purchase.quantity} / Price {money(purchase.price)}</div>
              </div>
              <strong>{money(purchase.totalAmount)}</strong>
            </div>
          ))}
          {!manualPurchases.length && <div className="text-sm text-zinc-500">No manual dealer purchases recorded.</div>}
        </div>
        <div className="space-y-3">
          {purchases.map((purchase) => (
            <div key={purchase.id} className="rounded-lg border border-white-800 bg-zinc-900/70 p-4">
              {editingPurchaseId === purchase.id ? (
                <div className="grid gap-3 md:grid-cols-[1fr_1fr_160px_auto]">
                  <input
                    className="field"
                    value={purchaseDrafts.supplierName || ''}
                    onChange={(event) => setPurchaseDrafts({ ...purchaseDrafts, supplierName: event.target.value })}
                    placeholder="Dealer name"
                  />
                  <input
                    className="field"
                    value={purchaseDrafts.dealerInvoiceNumber || ''}
                    onChange={(event) => setPurchaseDrafts({ ...purchaseDrafts, dealerInvoiceNumber: event.target.value })}
                    placeholder="Invoice no."
                  />
                  <input
                    className="field"
                    inputMode="decimal"
                    value={purchaseDrafts.grandTotal ?? ''}
                    onChange={(event) => setPurchaseDrafts({ ...purchaseDrafts, grandTotal: cleanNumberInput(event.target.value) })}
                    placeholder="Amount"
                  />
                  <div className="flex gap-2">
                    <button className="btn btn-secondary" onClick={() => saveEditedPurchase(purchase)}><Save size={16} /></button>
                    <button className="btn btn-danger" onClick={() => {
                      setEditingPurchaseId(null);
                      setPurchaseDrafts({});
                    }}>Cancel</button>
                  </div>
                </div>
              ) : (
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="font-bold">{purchase.supplierName}</div>
                    <div className="text-sm text-white-500">{purchase.dealerInvoiceNumber || 'No invoice no.'} / {purchase.purchaseDate}</div>
                  </div>
                  <div className="flex items-center gap-3">
                    <strong>{money(purchase.grandTotal)}</strong>
                    <button className="btn btn-secondary" onClick={() => startEditPurchase(purchase)}><Pencil size={16} /></button>
                  </div>
                </div>
              )}
              <div className="mt-3 text-xs text-zinc-500">{purchase.items.length} line item(s)</div>
            </div>
          ))}
          {!purchases.length && <div className="text-sm text-zinc-500">No dealer purchases recorded yet.</div>}
        </div>
      </section>
    </div>
  );
}

function DealerOrders({ orders, createDealerOrder, updateDealerOrder, deleteDealerOrder }) {
  const [dealerName, setDealerName] = useState('');
  const [orderDate, setOrderDate] = useState(new Date().toISOString().slice(0, 10));
  const [notes, setNotes] = useState('');
  const [draftItem, setDraftItem] = useState({ itemName: '', partNumber: '', quantity: '1', note: '' });
  const [items, setItems] = useState([]);
  const [message, setMessage] = useState('');
  const [editingOrderId, setEditingOrderId] = useState(null);
  const [editingOrderNumber, setEditingOrderNumber] = useState('');

  const addItem = () => {
    const itemName = draftItem.itemName.trim();
    const partNumber = draftItem.partNumber.trim().toUpperCase();
    const quantity = Math.max(1, Number(draftItem.quantity || 0));
    if (!itemName && !partNumber) {
      setMessage('Add an item name or part number.');
      return;
    }
    if (!quantity || quantity < 1) {
      setMessage('Quantity must be at least 1.');
      return;
    }
    setItems((current) => [...current, {
      itemName,
      partNumber,
      quantity,
      note: draftItem.note.trim()
    }]);
    setDraftItem({ itemName: '', partNumber: '', quantity: '1', note: '' });
    setMessage('');
  };

  const saveOrder = async () => {
    if (!items.length) {
      setMessage('Add at least one item before saving.');
      return;
    }
    const payload = { orderNumber: editingOrderNumber, dealerName, orderDate, notes, items };
    const order = editingOrderId
      ? await updateDealerOrder(editingOrderId, payload)
      : await createDealerOrder(payload);
    if (order) {
      setDealerName('');
      setOrderDate(new Date().toISOString().slice(0, 10));
      setNotes('');
      setItems([]);
      setDraftItem({ itemName: '', partNumber: '', quantity: '1', note: '' });
      setMessage('');
      setEditingOrderId(null);
      setEditingOrderNumber('');
    }
  };

  const printOrder = async () => {
    const payload = { orderNumber: editingOrderNumber, dealerName, orderDate, notes, items };
    if (editingOrderId) {
      const order = await updateDealerOrder(editingOrderId, payload);
      if (order) window.open(printUrlForOrder(order), '_blank');
      return;
    }
    if (!items.length) {
      setMessage('Add at least one item before printing.');
      return;
    }
    const order = await createDealerOrder(payload);
    if (order) window.open(printUrlForOrder(order), '_blank');
  };

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_1fr]">
      <section className="panel p-4">
        <div className="mb-4">
          <h2 className="text-lg font-black">Dealer Orders</h2>
          <p className="text-sm text-zinc-500">{editingOrderId ? 'Editing an existing dealer order. Add or remove items, then save.' : 'Build a next-order list for your dealer. Part number is optional.'}</p>
        </div>
        {message && <div className="mb-4 rounded-md border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-sm text-amber-100">{message}</div>}
        <div className="grid gap-3 md:grid-cols-[1fr_160px]">
          <input className="field" value={dealerName} onChange={(event) => setDealerName(event.target.value)} placeholder="Dealer name (optional)" />
          <input className="field" type="date" value={orderDate} onChange={(event) => setOrderDate(event.target.value)} />
          <input className="field md:col-span-2" value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Notes (optional)" />
        </div>

        <div className="mt-5 rounded-lg border border-zinc-800 bg-zinc-950/70 p-3">
          <div className="grid gap-3 md:grid-cols-[1.4fr_1fr_120px_1fr_auto]">
            <input
              className="field"
              value={draftItem.itemName}
              onChange={(event) => setDraftItem({ ...draftItem, itemName: event.target.value })}
              placeholder="Item name"
            />
            <input
              className="field"
              value={draftItem.partNumber}
              onChange={(event) => setDraftItem({ ...draftItem, partNumber: event.target.value.toUpperCase() })}
              placeholder="Part number (optional)"
            />
            <input
              className="field"
              type="number"
              min="1"
              value={draftItem.quantity}
              onChange={(event) => setDraftItem({ ...draftItem, quantity: cleanNumberInput(event.target.value) })}
              placeholder="Qty"
            />
            <input
              className="field"
              value={draftItem.note}
              onChange={(event) => setDraftItem({ ...draftItem, note: event.target.value })}
              placeholder="Note"
            />
            <button className="btn btn-secondary" type="button" onClick={addItem}>
              <PackageCheck size={16} /> Add Item
            </button>
          </div>
          <div className="mt-3 space-y-2">
            {items.map((item, index) => (
              <div key={`${item.partNumber || item.itemName}-${index}`} className="flex items-center justify-between gap-3 rounded-lg border border-zinc-800 bg-zinc-900 p-3 text-sm">
                <div>
                  <div className="font-semibold">{item.itemName || 'Unnamed item'}</div>
                  <div className="text-xs text-zinc-500">{item.partNumber || 'No part number'} / Qty {item.quantity}{item.note ? ` / ${item.note}` : ''}</div>
                </div>
                <button className="text-zinc-500 hover:text-red-300" type="button" onClick={() => setItems((current) => current.filter((_, i) => i !== index))}>
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
            {!items.length && <div className="rounded-lg border border-dashed border-zinc-800 p-8 text-center text-zinc-500">Add items for the next dealer order.</div>}
          </div>
        </div>

        <div className="mt-4 flex items-center justify-between">
          <span className="text-sm text-zinc-400">{items.length} item(s)</span>
          <div className="flex gap-2">
            {editingOrderId && (
              <button className="btn btn-secondary" type="button" onClick={() => {
                setEditingOrderId(null);
                setEditingOrderNumber('');
                setDealerName('');
                setOrderDate(new Date().toISOString().slice(0, 10));
                setNotes('');
                setItems([]);
                setDraftItem({ itemName: '', partNumber: '', quantity: '1', note: '' });
                setMessage('');
              }}>
                Cancel Edit
              </button>
            )}
            <button className="btn btn-primary" type="button" onClick={saveOrder}>
              <Save size={16} /> {editingOrderId ? 'Update Order' : 'Save Order'}
            </button>
            <button className="btn btn-secondary" type="button" onClick={printOrder}>
              <Printer size={16} /> Print Order
            </button>
          </div>
        </div>
      </section>

      <section className="panel p-4">
        <h2 className="mb-4 text-lg font-black">Saved Orders</h2>
        <div className="space-y-3">
          {orders.map((order) => (
            <div key={order.id} className="rounded-lg border border-zinc-800 bg-zinc-900/70 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="font-bold">{order.orderNumber}</div>
                  <div className="text-sm text-zinc-500">{order.dealerName || 'No dealer name'} / {formatBillDate(order.orderDate)}</div>
                  <div className="mt-1 text-xs text-zinc-400">{order.items.length} line item(s)</div>
                </div>
                <div className="flex items-center gap-2">
                  <button className="btn btn-secondary" onClick={() => {
                    setEditingOrderId(order.id);
                    setEditingOrderNumber(order.orderNumber || '');
                    setDealerName(order.dealerName || '');
                    setOrderDate(order.orderDate || new Date().toISOString().slice(0, 10));
                    setNotes(order.notes || '');
                    setItems((order.items || []).map((item) => ({
                      itemName: item.itemName || '',
                      partNumber: item.partNumber || '',
                      quantity: item.quantity || 1,
                      note: item.note || ''
                    })));
                    setDraftItem({ itemName: '', partNumber: '', quantity: '1', note: '' });
                    setMessage('');
                  }}>Edit</button>
                  <button className="btn btn-secondary" onClick={() => window.open(printUrlForOrder(order), '_blank')}><Printer size={16} /></button>
                  <button className="btn btn-danger" onClick={() => deleteDealerOrder(order)}><Trash2 size={16} /></button>
                </div>
              </div>
            </div>
          ))}
          {!orders.length && <div className="text-sm text-zinc-500">No saved dealer orders yet.</div>}
        </div>
      </section>
    </div>
  );
}

function OngoingBills({ bills, allParts, updateOngoingBillItems, recordPayment, finalizeOngoingBill, cancelBill }) {
  const [selectedId, setSelectedId] = useState('');
  const [draftItems, setDraftItems] = useState([]);
  const [partSearch, setPartSearch] = useState('');
  const [invoiceType, setInvoiceType] = useState('NORMAL');
  const [supplyType, setSupplyType] = useState('INTRA_STATE');
  const [payment, setPayment] = useState({ amount: '', paymentDate: new Date().toISOString().slice(0, 10), notes: '' });
  const selected = bills.find((bill) => String(bill.id) === String(selectedId)) || bills[0];

  useEffect(() => {
    if (!selected) {
      setDraftItems([]);
      return;
    }
    setSelectedId(String(selected.id));
    setDraftItems((selected.items || []).map((item) => ({
      partId: item.partId,
      partName: item.partName,
      partNumber: item.partNumber,
      quantity: item.quantity,
      unitPrice: item.unitPrice || 0,
      discountAmount: item.discountAmount || 0,
      lineTotal: item.lineTotal
    })));
    setInvoiceType(selected.invoiceType || 'NORMAL');
    setSupplyType(selected.supplyType || 'INTRA_STATE');
    setPayment({ amount: '', paymentDate: new Date().toISOString().slice(0, 10), notes: '' });
  }, [selected]);

  const matchingParts = partSearch.trim()
    ? allParts
        .filter((part) => `${part.name} ${part.companyName || ''} ${part.partNumber || ''} ${part.serialNo || ''} ${part.hsnCode || ''} ${part.rackNumber || ''} ${part.sellingPrice || ''}`.toLowerCase().includes(partSearch.trim().toLowerCase()))
        .slice(0, 12)
    : [];

  const addPart = async (part) => {
    if (!part || !selected) return;
    const nextItems = (() => {
      const existing = draftItems.find((item) => item.partId === part.id);
      if (existing) {
        return draftItems.map((item) => item.partId === part.id ? { ...item, quantity: Number(item.quantity || 0) + 1 } : item);
      }
      return [...draftItems, { partId: part.id, partName: part.name, partNumber: part.partNumber, quantity: 1, unitPrice: part.sellingPrice, discountAmount: 0, lineTotal: part.sellingPrice }];
    })();
    setDraftItems(nextItems);
    setPartSearch('');
    const updated = await updateOngoingBillItems(draftBill, nextItems);
    if (!updated) {
      setDraftItems((selected.items || []).map((item) => ({
        partId: item.partId,
        partName: item.partName,
        partNumber: item.partNumber,
        quantity: item.quantity,
        unitPrice: item.unitPrice || 0,
        discountAmount: item.discountAmount || 0,
        lineTotal: item.lineTotal
      })));
    }
  };

  const draftBill = selected ? { ...selected, invoiceType, supplyType } : selected;
  const itemAmount = (item) => {
    const gross = Number(item.unitPrice || 0) * Number(item.quantity || 0);
    return Math.max(0, gross - Number(item.discountAmount || 0));
  };

  if (!bills.length) {
    return <section className="panel p-6 text-zinc-500">No ongoing bills right now.</section>;
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
      <section className="panel p-4">
        <h2 className="mb-4 text-lg font-black">Ongoing Bills</h2>
        <div className="space-y-2">
          {bills.map((bill) => (
            <button key={bill.id} className={`w-full rounded-lg border p-3 text-left ${selected?.id === bill.id ? 'border-red-500 bg-red-500/10' : 'border-zinc-800 bg-zinc-900/50'}`} onClick={() => setSelectedId(String(bill.id))}>
              <div className="flex justify-between gap-3">
                <strong>{bill.jobReference || bill.customerName}</strong>
                <span>{money(bill.balanceAmount)}</span>
              </div>
              <div className="text-xs text-zinc-500">{bill.mechanicName || 'No mechanic'} / {bill.garageName || '-'} / {bill.billNumber}</div>
            </button>
          ))}
        </div>
      </section>

      {selected && (
        <section className="panel p-4">
          <div className="mb-4 flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
            <div>
              <h2 className="text-lg font-black">{selected.jobReference || selected.customerName}</h2>
              <p className="text-sm text-zinc-500">{selected.mechanicName} / {selected.garageName} / {formatBillDate(selected.billingDate)}</p>
            </div>
            <div className="text-right text-sm">
              <div>Total: <strong>{money(selected.grandTotal)}</strong></div>
              <div>Paid: <strong>{money(selected.amountPaid)}</strong></div>
              <div>Balance: <strong className="text-amber-300">{money(selected.balanceAmount)}</strong></div>
            </div>
          </div>

          <div className="mb-4 grid gap-2 rounded-lg border border-zinc-800 bg-zinc-900/40 p-3 md:grid-cols-[180px_180px]">
            <select className="field" value={invoiceType} onChange={(event) => setInvoiceType(event.target.value)}>
              <option value="NORMAL">Normal Bill</option>
              <option value="GST">GST Bill</option>
            </select>
            <select className="field" value={supplyType} onChange={(event) => setSupplyType(event.target.value)} disabled={invoiceType !== 'GST'}>
              <option value="INTRA_STATE">CGST + SGST</option>
              <option value="INTER_STATE">IGST</option>
            </select>
          </div>

          <div className="mb-4 rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
            <div className="relative">
              <Search className="absolute left-3 top-3 text-zinc-500" size={16} />
              <input
                className="field pl-9"
                value={partSearch}
                onChange={(event) => setPartSearch(event.target.value)}
                placeholder="Search item by name, part no, serial no, HSN, rack, or rate"
              />
            </div>
            {partSearch.trim() && (
              <div className="mt-3 max-h-80 overflow-y-auto rounded-lg border border-zinc-800">
                {matchingParts.map((part) => (
                  <button
                    key={part.id}
                    className="grid w-full gap-2 border-b border-zinc-800 bg-zinc-950/70 p-3 text-left hover:bg-zinc-900 md:grid-cols-[1fr_130px_100px_auto] md:items-center"
                    onClick={() => addPart(part)}
                    disabled={part.stockLevel < 1}
                  >
                    <div>
                      <div className="font-semibold text-zinc-100">{part.name}</div>
                      <div className="text-xs text-zinc-500">{part.companyName || '-'} / {part.partNumber || 'No part no.'} / Rack {part.rackNumber || '-'}</div>
                    </div>
                    <div className="text-sm text-zinc-300">{money(part.sellingPrice)}</div>
                    <div className={part.stockLevel < 1 ? 'text-sm text-red-300' : 'text-sm text-emerald-300'}>Stock {part.stockLevel}</div>
                    <span className="btn btn-secondary justify-center"><PackageCheck size={16} /> Add</span>
                  </button>
                ))}
                {!matchingParts.length && (
                  <div className="p-4 text-center text-sm text-zinc-500">No matching inventory item found.</div>
                )}
              </div>
            )}
          </div>

          <div className="space-y-2">
            {draftItems.map((item, index) => (
              <div key={`${item.partId}-${index}`} className="grid gap-2 rounded-lg border border-zinc-800 bg-zinc-900/60 p-3 md:grid-cols-[1fr_90px_120px_120px_130px_auto] md:items-center">
                <div>
                  <strong>{item.partName}</strong>
                  <div className="text-xs text-zinc-500">{item.partNumber || 'No part number'}</div>
                </div>
                <label className="space-y-1">
                  <span className="text-[10px] font-semibold uppercase text-zinc-500">Qty</span>
                  <input className="field" inputMode="numeric" value={item.quantity} onChange={(event) => setDraftItems((items) => items.map((row, rowIndex) => rowIndex === index ? { ...row, quantity: cleanNumberInput(event.target.value) } : row))} />
                </label>
                <div>
                  <div className="text-[10px] font-semibold uppercase text-zinc-500">Rate</div>
                  <div className="font-bold text-zinc-100">{money(item.unitPrice)}</div>
                </div>
                <div>
                  <div className="text-[10px] font-semibold uppercase text-zinc-500">Amount</div>
                  <div className="font-bold text-zinc-100">{money(itemAmount(item))}</div>
                </div>
                <label className="space-y-1">
                  <span className="text-[10px] font-semibold uppercase text-zinc-500">Discount</span>
                  <input className="field" inputMode="decimal" value={item.discountAmount || ''} onChange={(event) => setDraftItems((items) => items.map((row, rowIndex) => rowIndex === index ? { ...row, discountAmount: cleanSignedNumberInput(event.target.value) } : row))} placeholder="Discount" />
                </label>
                <button className="btn btn-danger" onClick={async () => {
                  const nextItems = draftItems.filter((_, rowIndex) => rowIndex !== index);
                  setDraftItems(nextItems);
                  const updated = await updateOngoingBillItems(draftBill, nextItems);
                  if (!updated) {
                    setDraftItems((selected.items || []).map((row) => ({
                      partId: row.partId,
                      partName: row.partName,
                      partNumber: row.partNumber,
                      quantity: row.quantity,
                      unitPrice: row.unitPrice || 0,
                      discountAmount: row.discountAmount || 0,
                      lineTotal: row.lineTotal
                    })));
                  }
                }}><Trash2 size={16} /></button>
              </div>
            ))}
          </div>

          <div className="mt-4 flex flex-col gap-2 md:flex-row md:justify-end">
            <button className="btn btn-primary" onClick={() => updateOngoingBillItems(draftBill, draftItems)}><Save size={16} /> Save Changes</button>
          </div>

          <div className="mt-6 grid gap-3 rounded-lg border border-zinc-800 bg-zinc-900/40 p-3 md:grid-cols-[1fr_170px_1fr_auto]">
            <input className="field" inputMode="decimal" value={payment.amount} onChange={(event) => setPayment({ ...payment, amount: cleanNumberInput(event.target.value) })} placeholder="Amount received" />
            <input className="field" type="date" value={payment.paymentDate} onChange={(event) => setPayment({ ...payment, paymentDate: event.target.value })} />
            <input className="field" value={payment.notes} onChange={(event) => setPayment({ ...payment, notes: event.target.value })} placeholder="Payment notes" />
            <button className="btn btn-secondary" onClick={() => recordPayment(selected, { ...payment, amount: Number(payment.amount || 0) })}><IndianRupee size={16} /> Record Payment</button>
          </div>

          <div className="mt-4 flex justify-end">
            <div className="flex flex-col gap-2 md:flex-row">
              <button className="btn btn-danger" onClick={() => cancelBill(selected)}>
                <Trash2 size={16} /> Delete Ongoing Bill
              </button>
              <button className="btn btn-secondary" onClick={() => window.open(printUrlForBill(selected), '_blank')}>
                <Printer size={16} /> Print Bill
              </button>
              <button className="btn btn-primary" disabled={Number(selected.balanceAmount || 0) > 0} onClick={() => finalizeOngoingBill(selected)}>
                <Printer size={16} /> Finalize Bill
              </button>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}

function ManageClients({ mechanics, createMechanic, updateMechanic, deleteMechanic, ongoingBills, bills, recordPayment }) {
  const [form, setForm] = useState({ mechanicName: '', garageName: '' });
  const [editingId, setEditingId] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [selectedCustomerKey, setSelectedCustomerKey] = useState('');
  const [customerPaymentDrafts, setCustomerPaymentDrafts] = useState({});
  const selected = mechanics.find((mechanic) => String(mechanic.id) === String(selectedId));
  const selectedOngoing = selected ? ongoingBills.filter((bill) => bill.mechanicId === selected.id) : [];
  const selectedCompleted = selected ? bills.filter((bill) => bill.mechanicId === selected.id) : [];
  const creditCustomers = useMemo(() => {
    const grouped = new Map();
    bills
      .filter((bill) => bill.status !== 'CANCELLED' && Number(bill.balanceAmount || 0) > 0)
      .forEach((bill) => {
        const key = `${(bill.customerName || 'Walk-in Customer').trim()}|${bill.customerMobile || ''}`;
        const current = grouped.get(key) || {
          key,
          customerName: bill.customerName || 'Walk-in Customer',
          customerMobile: bill.customerMobile || '',
          outstanding: 0,
          bills: []
        };
        current.outstanding += Number(bill.balanceAmount || 0);
        current.bills.push(bill);
        grouped.set(key, current);
      });
    return [...grouped.values()].sort((a, b) => b.outstanding - a.outstanding);
  }, [bills]);
  const selectedCustomer = creditCustomers.find((customer) => customer.key === selectedCustomerKey) || creditCustomers[0];

  const save = async () => {
    const saved = editingId ? await updateMechanic(editingId, form) : await createMechanic(form);
    if (saved) {
      setForm({ mechanicName: '', garageName: '' });
      setEditingId('');
    }
  };

  return (
    <div className="space-y-5">
      <section className="panel p-4">
        <h2 className="mb-3 text-lg font-black">Manage Clients</h2>
        <div className="grid gap-2 md:grid-cols-[1fr_1fr_auto]">
          <input className="field" value={form.mechanicName} onChange={(event) => setForm({ ...form, mechanicName: event.target.value })} placeholder="Mechanic name" />
          <input className="field" value={form.garageName} onChange={(event) => setForm({ ...form, garageName: event.target.value })} placeholder="Garage name" />
          <button className="btn btn-primary" onClick={save}><Save size={16} /> {editingId ? 'Update' : 'Add Mechanic'}</button>
        </div>
      </section>

      <section className="panel p-4">
        <h2 className="mb-3 text-lg font-black">Credit Customers</h2>
        <div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
          <div className="overflow-x-auto rounded-lg border border-zinc-800">
            <table className="w-full min-w-[560px] text-left text-sm">
              <thead className="bg-zinc-900 text-xs uppercase text-zinc-500">
                <tr><th className="px-3 py-3">Customer</th><th>Mobile</th><th className="text-right">Pending</th><th className="text-right">Bills</th></tr>
              </thead>
              <tbody className="divide-y divide-zinc-800">
                {creditCustomers.map((customer) => (
                  <tr key={customer.key} className={selectedCustomer?.key === customer.key ? 'bg-zinc-900' : ''}>
                    <td className="px-3 py-3"><button className="font-semibold hover:text-red-300" onClick={() => setSelectedCustomerKey(customer.key)}>{customer.customerName}</button></td>
                    <td>{customer.customerMobile || '-'}</td>
                    <td className="text-right">{money(customer.outstanding)}</td>
                    <td className="text-right">{customer.bills.length}</td>
                  </tr>
                ))}
                {!creditCustomers.length && (
                  <tr><td colSpan="4" className="px-3 py-8 text-center text-zinc-500">No pending customer credit bills.</td></tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="space-y-3">
            {selectedCustomer?.bills.map((bill) => {
              const draft = customerPaymentDrafts[bill.id] || {};
              return (
                <div key={bill.id} className="rounded-lg border border-zinc-800 bg-zinc-900/60 p-3">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="font-bold">{billLabel(bill)} <span className="text-amber-300">{bill.status}</span></div>
                      <div className="text-sm text-zinc-500">{formatBillDate(bill.billingDate)} / Total {money(bill.grandTotal)} / Balance {money(bill.balanceAmount)}</div>
                    </div>
                    <button className="btn btn-secondary" onClick={() => window.open(printUrlForBill(bill), '_blank')}><Printer size={16} /></button>
                  </div>
                  <div className="mt-3 grid gap-2 md:grid-cols-[130px_150px_1fr_auto]">
                    <input className="field" inputMode="decimal" value={draft.amount || ''} onChange={(event) => setCustomerPaymentDrafts({ ...customerPaymentDrafts, [bill.id]: { ...draft, amount: cleanNumberInput(event.target.value) } })} placeholder="Amount" />
                    <input className="field" type="date" value={draft.paymentDate || new Date().toISOString().slice(0, 10)} onChange={(event) => setCustomerPaymentDrafts({ ...customerPaymentDrafts, [bill.id]: { ...draft, paymentDate: event.target.value } })} />
                    <input className="field" value={draft.notes || ''} onChange={(event) => setCustomerPaymentDrafts({ ...customerPaymentDrafts, [bill.id]: { ...draft, notes: event.target.value } })} placeholder="Payment notes" />
                    <button className="btn btn-secondary" onClick={async () => {
                      const updated = await recordPayment(bill, { amount: Number(draft.amount || 0), paymentDate: draft.paymentDate || new Date().toISOString().slice(0, 10), notes: draft.notes || '' });
                      if (updated) setCustomerPaymentDrafts((current) => ({ ...current, [bill.id]: { amount: '', paymentDate: new Date().toISOString().slice(0, 10), notes: '' } }));
                    }}>
                      <IndianRupee size={16} /> Record
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      <section className="panel p-4">
        <div className="overflow-x-auto rounded-lg border border-zinc-800">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="bg-zinc-900 text-xs uppercase text-zinc-500">
              <tr><th className="px-3 py-3">Mechanic</th><th>Garage</th><th className="text-right">Outstanding</th><th className="text-right">Ongoing</th><th className="text-right">Completed</th><th className="text-right">Actions</th></tr>
            </thead>
            <tbody className="divide-y divide-zinc-800">
              {mechanics.map((mechanic) => (
                <tr key={mechanic.id}>
                  <td className="px-3 py-3"><button className="font-semibold text-zinc-100 hover:text-red-300" onClick={() => setSelectedId(String(mechanic.id))}>{mechanic.mechanicName}</button></td>
                  <td>{mechanic.garageName}</td>
                  <td className="text-right">{money(mechanic.totalOutstanding)}</td>
                  <td className="text-right">{mechanic.ongoingBills}</td>
                  <td className="text-right">{mechanic.completedBills}</td>
                  <td className="px-3 py-3 text-right">
                    <button className="btn btn-secondary mr-2" onClick={() => { setEditingId(mechanic.id); setForm({ mechanicName: mechanic.mechanicName, garageName: mechanic.garageName }); }}><Pencil size={16} /></button>
                    <button className="btn btn-danger" onClick={() => deleteMechanic(mechanic)}><Trash2 size={16} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {selected && (
        <section className="panel p-4">
          <h2 className="mb-3 text-lg font-black">{selected.mechanicName} / {selected.garageName}</h2>
          <div className="mb-4 grid gap-3 md:grid-cols-3">
            <Stat icon={IndianRupee} label="Outstanding" value={money(selected.totalOutstanding)} tone="text-amber-300" />
            <Stat icon={ReceiptText} label="Ongoing Bills" value={selected.ongoingBills} tone="text-red-300" />
            <Stat icon={ClipboardList} label="Completed Bills" value={selected.completedBills} tone="text-emerald-300" />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <SimpleList title="Ongoing Jobs" items={selectedOngoing.map((bill) => `${bill.jobReference || bill.customerName} / ${formatBillDate(bill.billingDate)} / ${money(bill.balanceAmount)}`)} />
            <SimpleList title="Completed Bills" items={selectedCompleted.map((bill) => `${bill.billNumber} / ${formatBillDate(bill.billingDate)} / ${money(bill.grandTotal)}`)} />
          </div>
        </section>
      )}
    </div>
  );
}

function BillHistory({ bills, loadBills, cancelBill, deleteCancelledBill, recordPayment }) {
  const [page, setPage] = useState(1);
  const [filter, setFilter] = useState('ALL');
  const [filterDate, setFilterDate] = useState('');
  const [filterMonth, setFilterMonth] = useState('');
  const [paymentDrafts, setPaymentDrafts] = useState({});
  const filteredBills = bills.filter((bill) => {
    if (filter === 'PAID') return bill.status === 'PAID' || bill.status === 'FULLY_PAID';
    if (filter === 'PENDING') return bill.status === 'PENDING' || bill.status === 'PARTIALLY_PAID';
    if (filter === 'CANCELLED') return bill.status === 'CANCELLED';
    return true;
  }).filter((bill) => {
    if (filterDate && bill.billingDate !== filterDate) return false;
    if (filterMonth && (!bill.billingDate || !bill.billingDate.startsWith(filterMonth))) return false;
    return true;
  });
  const totalPages = Math.max(1, Math.ceil(filteredBills.length / PAGE_SIZE));
  const pagedBills = filteredBills.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  useEffect(() => {
    if (page > totalPages) setPage(totalPages);
  }, [page, totalPages]);

  useEffect(() => {
    setPage(1);
  }, [filter, filterDate, filterMonth]);

  useEffect(() => {
    const query = filterDate
      ? `?date=${encodeURIComponent(filterDate)}`
      : filterMonth
        ? `?month=${encodeURIComponent(filterMonth)}`
        : '';
    loadBills(query).catch(() => {});
  }, [filterDate, filterMonth, loadBills]);

  return (
    <section className="panel p-4">
      <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <h2 className="text-lg font-black">Bill History</h2>
        <div className="grid w-full gap-2 md:w-auto md:grid-cols-[180px_150px_150px]">
          <select className="field" value={filter} onChange={(event) => setFilter(event.target.value)}>
            <option value="ALL">All bills</option>
            <option value="PAID">Paid</option>
            <option value="PENDING">Pending / partial</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
          <input className="field" type="date" value={filterDate} onChange={(event) => setFilterDate(event.target.value)} title="Invoice date" />
          <input className="field" type="month" value={filterMonth} onChange={(event) => setFilterMonth(event.target.value)} title="Invoice month" />
        </div>
      </div>
      <div className="space-y-3">
        {pagedBills.map((bill) => (
          <div key={bill.id} className="flex flex-col gap-3 rounded-lg border border-zinc-800 bg-zinc-900/60 p-4 md:flex-row md:items-center md:justify-between">
            <div>
              <div className="font-bold">{billLabel(bill)} <span className={bill.status === 'CANCELLED' ? 'text-red-300' : 'text-emerald-300'}>{bill.status}</span></div>
              <div className="text-sm text-zinc-500">{bill.customerName} / {formatBillDate(bill.billingDate)}</div>
              <div className="mt-1 text-xs text-zinc-400">Paid {money(bill.amountPaid)} / Balance {money(bill.balanceAmount)}</div>
              {Number(bill.balanceAmount || 0) > 0 && bill.status !== 'CANCELLED' && (
                <div className="mt-3 grid gap-2 md:grid-cols-[130px_150px_1fr_auto]">
                  <input className="field" inputMode="decimal" value={paymentDrafts[bill.id]?.amount || ''} onChange={(event) => setPaymentDrafts({ ...paymentDrafts, [bill.id]: { ...(paymentDrafts[bill.id] || {}), amount: cleanNumberInput(event.target.value) } })} placeholder="Amount" />
                  <input className="field" type="date" value={paymentDrafts[bill.id]?.paymentDate || new Date().toISOString().slice(0, 10)} onChange={(event) => setPaymentDrafts({ ...paymentDrafts, [bill.id]: { ...(paymentDrafts[bill.id] || {}), paymentDate: event.target.value } })} />
                  <input className="field" value={paymentDrafts[bill.id]?.notes || ''} onChange={(event) => setPaymentDrafts({ ...paymentDrafts, [bill.id]: { ...(paymentDrafts[bill.id] || {}), notes: event.target.value } })} placeholder="Payment notes" />
                  <button className="btn btn-secondary" onClick={async () => {
                    const draft = paymentDrafts[bill.id] || {};
                    const updated = await recordPayment(bill, { amount: Number(draft.amount || 0), paymentDate: draft.paymentDate || new Date().toISOString().slice(0, 10), notes: draft.notes || '' });
                    if (updated) setPaymentDrafts((current) => ({ ...current, [bill.id]: { amount: '', paymentDate: new Date().toISOString().slice(0, 10), notes: '' } }));
                  }}>
                    <IndianRupee size={16} /> Pay
                  </button>
                </div>
              )}
            </div>
            <div className="flex items-center gap-2">
              <strong>{money(billAmount(bill))}</strong>
              <button className="btn btn-secondary" onClick={() => window.open(printUrlForBill(bill), '_blank')}><Printer size={16} /></button>
              <button className="btn btn-danger" onClick={() => cancelBill(bill)} disabled={bill.status === 'CANCELLED'}>Cancel</button>
              {bill.status === 'CANCELLED' && (
                <button className="btn btn-danger" onClick={() => deleteCancelledBill(bill)}>Delete</button>
              )}
            </div>
          </div>
        ))}
      </div>
      <Pagination page={page} totalItems={filteredBills.length} pageSize={PAGE_SIZE} onPageChange={setPage} />
    </section>
  );
}

function Suppliers({ suppliers, createSupplier }) {
  return (
    <div className="grid gap-6 xl:grid-cols-[0.85fr_1.15fr]">
      <form className="panel space-y-3 p-4" onSubmit={createSupplier}>
        <h2 className="text-lg font-black">New Supplier</h2>
        <input className="field" name="name" placeholder="Supplier name" required />
        <input className="field" name="contactPerson" placeholder="Contact person" />
        <input className="field" name="phone" placeholder="Phone" />
        <input className="field" name="address" placeholder="Address" />
        <input className="field" name="defaultDiscount" type="number" min="0" step="0.01" placeholder="Default purchase discount %" />
        <button className="btn btn-primary w-full">Save Supplier</button>
      </form>
      <section className="panel p-4">
        <h2 className="mb-4 text-lg font-black">Suppliers</h2>
        <div className="grid gap-3 md:grid-cols-2">
          {suppliers.map((supplier) => (
            <div key={supplier.id} className="rounded-lg border border-zinc-800 bg-zinc-900 p-4">
              <div className="font-bold">{supplier.name}</div>
              <div className="text-sm text-zinc-500">{supplier.contactPerson || 'No contact'} / {supplier.phone || 'No phone'}</div>
              <div className="mt-3 text-sm text-emerald-300">{supplier.defaultDiscount}% default discount</div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

export default App;
